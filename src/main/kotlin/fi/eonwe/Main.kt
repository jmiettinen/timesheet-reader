package fi.eonwe

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.logging.Level

fun main(args: Array<String>) {
    val settings = CmdLineSettings()

    val jc = JCommander(settings)
    jc.setProgramName(Common.programName)
    try {
        jc.parse(*args)
    } catch (e : ParameterException) {
        print(e)
        return
    }
    if (settings.showHelp) {
        jc.usage()
        System.exit(1)
    }
    var failure = false
    if (settings.hoursPerWeek < 0) {
        print("Working hours per week must be >= 0. Was ${settings.hoursPerWeek}")
        failure = true
    }
    if (settings.dailyHoursAlert < 0) {
        print("Minimum hours pers day must be >= 0. Was ${settings.dailyHoursAlert}")
        failure = true
    }
    if (settings.weeklyHoursAlert < 0) {
        print("Minimum hours per week must be >= 0. Was ${settings.weeklyHoursAlert}")
        failure = true
    }
    val durationField = settings.durationField
    if (durationField == null) {
        println("Must give a duration column")
        failure = true
    }
    val dateField = settings.dateField
    if (dateField == null) {
        println("Must give a date column")
        failure = true
    }
    val fileNames = settings.rest.toList()
    if (fileNames.isEmpty()) {
        failure = true
        println("No files given")
    }
    val files = fileNames.map { fn ->
        try {
            Pair(fn, FileInputStream(fn))
        } catch (e: FileNotFoundException) {
            Pair(fn, null)
        }
    }
    val unreadableFiles = files.filter { pair -> pair.second == null }
    if (unreadableFiles.isNotEmpty()) {
        println("Cannot read [${unreadableFiles.map { it.first }.joinToString(", ")}]")
        failure = true
    }
    if (failure) {
        jc.usage()
        System.exit(2)
    }
    if (settings.printDebug) {
        Common.logger.level = Level.FINEST
    } else {
        Common.logger.level = Level.WARNING
    }
    files.forEach { pair ->
        pair.second!!.use {
            BufferedInputStream(it).use {
                val result = calculateBalance(
                        input = it,
                        inputName = pair.first,
                        durationName = durationField!!,
                        dateName = dateField!!,
                        expectedPerWeek = settings.hoursPerWeek,
                        minDaily = settings.dailyHoursAlert,
                        minWeekly = settings.weeklyHoursAlert)
                println("File: ${result.input}")
                println("Balance: ${prettyHours(result.balance)}")
            }
        }
    }
}

class CmdLineSettings {

    @Parameter(names = arrayOf("--hours", "-H"), description="Hours per week")
    @JvmField var hoursPerWeek: Double = 40.0

    @Parameter(names = arrayOf("--min-daily-hours", "-d"), description = "Minimum hours per day to alert")
    @JvmField var dailyHoursAlert: Double = 0.0


    @Parameter(names = arrayOf("--min-weekly-hours", "-m"), description = "Minimum hours per week to alert")
    @JvmField var weeklyHoursAlert: Double = 0.0

    @Parameter(names = arrayOf("--duration-column", "-D"), description = "Name of the duration column")
    @JvmField var durationField: String? = null

    @Parameter(names = arrayOf("--date-column", "-A"), description = "Name of the date column")
    @JvmField var dateField: String? = null

    @Parameter(names = arrayOf("--debug"), description = "Debug to stdout")
    @JvmField var printDebug: Boolean = false

    @Parameter(names = arrayOf("--help", "-h"), description = "Show help")
    @JvmField var showHelp: Boolean = false

    @Parameter
    @JvmField var rest: List<String> = arrayListOf()

}
