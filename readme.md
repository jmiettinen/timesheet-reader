A tool to compute current time balance from Excel exports from [Timesheet.](https://play.google.com/store/apps/details?id=com.rauscha.apps.timesheet).

Usage
```
mvn package
java -jar target/timesheet-reader.jar \
--date-column "MyDateColumn" \
--duration-column "MyDurationColumn" MyFile.xls
```
