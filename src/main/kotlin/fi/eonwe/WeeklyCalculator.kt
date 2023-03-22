package fi.eonwe

import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

data class WeeklyReport(val input: String, val balance: Double, val skippedRows: Int, val daysPerWeek: Map<LocalDate, Map<LocalDate, Double>>)

fun dateToStartOfTheWeek(date: LocalDate): LocalDate {
    val wf = WeekFields.of(Locale.getDefault()).dayOfWeek()
    return date.with(wf, 1)
}

fun prettyHours(hours: Double, decimals: Int = 2): String {
    val effectiveDecimals = if (decimals < 0) 0 else decimals
    val template = "%.${effectiveDecimals}f"
    return template.format(hours)
}

fun calculateBalance(input: InputStream, inputName: String, durationName: String, dateName: String, expectedPerWeek: Double, minDaily: Double, minWeekly: Double, minYear: Int): WeeklyReport {
    var balance = 0.0

    var foundAny = false
    val logger = Common.logger
    val workbook = HSSFWorkbook(input)
    val resultMap = mutableMapOf<LocalDate, MutableMap<LocalDate, Double>>()
    var skippedRows = 0
    for (sheetIndex: Int in (0 until workbook.numberOfSheets)) {
        val sheet = workbook.getSheetAt(sheetIndex)
        val rowCount = sheet.physicalNumberOfRows
        if (rowCount > 0) {
            val firstRow = sheet.getRow(0)
            // Assume this is the header row
            var durationIndexMaybe: Int? = null
            var dateIndexMaybe: Int? = null
            for (c: Int in (0 until firstRow.lastCellNum)) {
                val cell = firstRow.getCell(c)
                if (cell?.cellType == CellType.STRING) {
                    val asString = cell.stringCellValue
                    if (asString == durationName) {
                        durationIndexMaybe = c
                    } else if (asString == dateName) {
                        dateIndexMaybe = c
                    }
                }
            }

            if (durationIndexMaybe == null || dateIndexMaybe == null) {
                continue
            } else {
                foundAny = true
            }
            val dateIndex = dateIndexMaybe
            val durationIndex = durationIndexMaybe
            for (r: Int in (1 until rowCount)) {
                val row = sheet.getRow(r) ?: continue
                val data = fetchDurationAndDate(row, dateIndex, durationIndex, inputName, sheetIndex, minYear)
                if (data != null) {
                    val startOfWeek = dateToStartOfTheWeek(data.first)
                    var existingMap = resultMap[startOfWeek]
                    if (existingMap == null) {
                        existingMap = mutableMapOf()
                        resultMap[startOfWeek] = existingMap
                    }
                    var currentCounter = existingMap.getOrElse(data.first) { 0.0 }
                    currentCounter += data.second
                    existingMap[data.first] = currentCounter
                } else {
                    skippedRows++
                }
            }
        }
    }
    if (!foundAny) {
        logger.warning("Did not find any columns with names $durationName, $dateName in $inputName")
    }
    for (weekStart in resultMap.keys.sorted()) {
        val map = resultMap[weekStart]!!
        val weekSum = map.values.sum()
        if (weekSum > Common.maxHoursPerWeek) {
            logger.warning { "Week starting from $weekStart is unexpectedly large ($weekSum)" }
        }
        val diff = weekSum - expectedPerWeek
        if (diff < 0) {
            logger.info {
                "Week starting from $weekStart is ${prettyHours(diff)} under expected amount"
            }
        }
        if (weekSum < minWeekly) {
            println("Week starting from $weekStart has ${prettyHours(weekSum)} worked hours (less than alert level ${prettyHours(minWeekly)})")
        }
        for (day in map.keys.sorted()) {
            val sum = map[day]!!
            if (sum < minDaily && (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY)) {
                // Just assume the workdays here
                println("Day $day has ${prettyHours(sum)} worked hours (less than alert level ${prettyHours(minDaily)})")
            }
        }
        balance += diff
    }

    return WeeklyReport(inputName, balance, skippedRows, mapOf())
}

fun fetchDurationAndDate(row: HSSFRow, dateIndex: Int, durationIndex: Int, inputName: String, sheetIndex: Int, minYear: Int): Pair<LocalDate, Double>? {
    val dateCell = row.getCell(dateIndex)
    val durationCell = row.getCell(durationIndex)
    val contextStart = { "Workbook $inputName, sheet $sheetIndex, row ${row.rowNum}" }
    val log = Common.logger
    if (dateCell == null || durationCell == null) {
        log.info() {
            "${contextStart.invoke()}, " +
                    "duration = ${if (durationCell == null) "null" else "exists" }, " +
                    "date = ${if (dateCell == null) "null" else "exists"}"
        }
        return null
    }
    val date: LocalDate
    val duration: Double
    if (dateCell.cellType == CellType.NUMERIC) {
        date = dateCell.localDateTimeCellValue.toLocalDate()
        if (date.year < minYear) {
            log.info { "Odd date $date encountered, will be ignored"}
            return null
        }
    } else {
        log.info { "${contextStart.invoke()} cell type of date column is ${dateCell.cellType}" }
        return null
    }
    if (durationCell.cellType == CellType.NUMERIC) {
        duration = durationCell.numericCellValue * 24
    } else {
        log.info { "${contextStart.invoke()} cell type of duration column is ${durationCell.cellType}" }
        return null

    }
    return date to duration
}
