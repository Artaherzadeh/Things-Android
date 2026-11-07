# مرجع دیتابیس Things-Android (SQLite، کاتلین)

منبع: تحلیل فایل `main.sqlite` استخراج‌شده از نسخه‌ی iPad. این سند برای ساخت یک دیتابیس هم‌ساختار در اپ اندروید (Kotlin) نوشته شده است. هر ادعا یکی از این برچسب‌ها را دارد: **\[تأییدشده\]** یعنی مستقیم از فایل خوانده یا روی داده تست شده، و **\[حدس\]** یعنی از الگوی داده استنباط شده و باید قبل از تکیه کردن تست شود.

## ۱. خلاصه‌ی فایل مرجع

- فرمت: SQLite 3، حالت journal برابر **WAL**، `page_size = 4096`، رمزگذاری UTF-8، `user_version = 0`، `foreign_keys` خاموش. \[تأییدشده\]
- نسخه‌ی اسکیمای خود اپ در جدول `Meta` با کلید `databaseVersion` و مقدار plist عددی **۳۰** ذخیره شده است. \[تأییدشده\]
- هیچ foreign key واقعی در DDL نیست؛ روابط با ستون‌های TEXT برقرارند. \[تأییدشده\]
- شناسه‌ها TEXT و ۲۲ کاراکتری‌اند، مثل `27Siu67GtebpFwH2oghPs6`. \[تأییدشده\]
- حجم ردیف‌ها در فایل نمونه:

| جدول | ردیف |
| --- | --- |
| TMTask | 689 |
| TMChecklistItem | 269 |
| TMTombstone | 191 |
| TMTaskTag | 122 |
| TMTag | 6 |
| TMArea | 3 |
| Meta | 3 |
| TMMetaItem | 2 |
| TMSettings | 1 |
| BSSyncronyMetadata | 11 |
| TMAreaTag, TMContact, TMSmartList | 0 |

## ۲. تصمیم معماری: Room یا SQLite خام؟

اگر هدف **عین همین اسکیما** است، Room مناسب نیست، به این دلایل:

1. `TMTaskTag` و `TMAreaTag` کلید اصلی ندارند، ولی Room برای هر Entity حداقل یک PK می‌خواهد.
2. ایندکس‌های partial (با `WHERE`) را Room نمی‌تواند تعریف کند.
3. `BSSpotlightDirtyEntity` از نوع `WITHOUT ROWID` است.
4. Room با `createFromAsset` اسکیمای فایل را با Entityها مقایسه می‌کند و در صورت کوچک‌ترین اختلاف `IllegalStateException` می‌دهد.

**توصیه:** DDL را عیناً در `assets/schema.sql` نگه دار و با `SQLiteOpenHelper` (یا SQLDelight) بالا بیاور. اگر Room را ترجیح می‌دهی، یک اسکیمای Room-friendly جدا داشته باش و برای هم‌ساختار شدن با Things یک لایه‌ی Import/Export بنویس. در ادامه‌ی سند فرض بر مسیر اول (SQLite خام) است.

## ۳. اسکیمای کامل (DDL)

این را در `app/src/main/assets/schema.sql` بگذار. بین دستورها خط جداکننده‌ی `-- @@` قرار داده شده تا parse کردن امن باشد (در متن DDL نقطه‌ویرگول هست و split روی `;` خطرناک است). فقط جدول‌های اصلی و سینک آمده‌اند؛ جدول‌های مخصوص iOS در بخش ۳.۳ هستند.

### ۳.۱ جدول‌ها

```sql
CREATE TABLE 'Meta' (
    'key'                 TEXT PRIMARY KEY,
    'value'               TEXT
)
-- @@
CREATE TABLE 'TMArea' (
    'uuid'                 TEXT PRIMARY KEY,
    'title'                TEXT,
    'visible'              INTEGER,
    'index'                INTEGER
, 'cachedTags' BLOB, experimental BLOB)
-- @@
CREATE TABLE 'TMAreaTag' (
    'areas'                TEXT NOT NULL,
    'tags'                 TEXT NOT NULL
)
-- @@
CREATE TABLE 'TMTag' (
    'uuid'                 TEXT PRIMARY KEY,
    'title'                TEXT,
    'shortcut'             TEXT,
    'usedDate'             REAL,
    'parent'               TEXT,
    'index'                INTEGER
, experimental BLOB)
-- @@
CREATE TABLE 'TMTaskTag' (
    'tasks'                TEXT NOT NULL,
    'tags'                 TEXT NOT NULL
)
-- @@
CREATE TABLE 'TMChecklistItem' (
    'uuid'                 TEXT PRIMARY KEY,
    'userModificationDate' REAL,
    'creationDate'         REAL,
    'title'                TEXT,
    'status'               INTEGER,
    'stopDate'             REAL,
    'index'                INTEGER,
    'task'                 TEXT
, 'leavesTombstone' INTEGER, experimental BLOB)
-- @@
CREATE TABLE 'TMContact' (
    'uuid'                 TEXT PRIMARY KEY,
    'displayName'          TEXT,
    'firstName'            TEXT,
    'lastName'             TEXT,
    'emails'               TEXT,
    'appleAddressBookId'   TEXT,
    'index'                INTEGER
)
-- @@
CREATE TABLE 'TMMetaItem' (
    'uuid'                 TEXT PRIMARY KEY,
    'value'                BLOB
)
-- @@
CREATE TABLE 'TMSettings' (
    'uuid'                 TEXT PRIMARY KEY,
    'logInterval'          INTEGER,
    'manualLogDate'        REAL
, 'groupTodayByParent' INTEGER, 'uriSchemeAuthenticationToken' TEXT, experimental BLOB)
-- @@
CREATE TABLE TMSmartList (

    "uuid"          TEXT PRIMARY KEY,

    "title"         TEXT,
    "index"         INTEGER,
    "definition"    BLOB,

    "experimental"  BLOB
)
-- @@
CREATE TABLE 'TMTombstone' (
    'uuid'          TEXT PRIMARY KEY,
    'deletionDate'  REAL,
    'deletedObjectUUID' TEXT
)
-- @@
CREATE TABLE "BSSyncronyMetadata" (
    'uuid'                 TEXT PRIMARY KEY,
    'value'                BLOB
)
```

### ۳.۲ جدول TMTask (قلب دیتابیس)

کامنت‌ها بخشی از DDL اصلی‌اند؛ نگهشان دار تا `sqlite_master` با فایل مرجع یکی بماند.

```sql
CREATE TABLE TMTask (

        "uuid"                              TEXT PRIMARY KEY,
        "leavesTombstone"                   INTEGER,

        "creationDate"                      REAL,
        "userModificationDate"              REAL,

        "type"                              INTEGER,

        "status"                            INTEGER,
        "stopDate"                          REAL,

        "trashed"                           INTEGER,

        "title"                             TEXT,
        "notes"                             TEXT,
        "notesSync"                         INTEGER,

        "cachedTags"                        BLOB,

        "start"                             INTEGER,
        "startDate"                         INTEGER,   -- REAL -> INTEGER
        "startBucket"                       INTEGER,
        "reminderTime"                      INTEGER,
        "lastReminderInteractionDate"       REAL,      -- Renamed from "lastAlarmInteractionDate"

        "deadline"                          INTEGER,   -- Renamed from "dueDate", REAL -> INTEGER
        "deadlineSuppressionDate"           INTEGER,   -- Renamed from "dueDateSuppressionDate", REAL -> INTEGER
        "t2_deadlineOffset"                 INTEGER,   -- Renamed from "dueDateOffset"

        "index"                             INTEGER,
        "todayIndex"                        INTEGER,
        "todayIndexReferenceDate"           INTEGER,   -- REAL -> INTEGER

        "area"                              TEXT,
        "project"                           TEXT,
        "heading"                           TEXT,      -- Renamed from "actionGroup"
        "contact"                           TEXT,      -- Renamed from "delegate"

        "untrashedLeafActionsCount"         INTEGER,
        "openUntrashedLeafActionsCount"     INTEGER,

        "checklistItemsCount"               INTEGER,
        "openChecklistItemsCount"           INTEGER,

        "rt1_repeatingTemplate"             TEXT,      -- Renamed from "repeatingTemplate"
        "rt1_recurrenceRule"                BLOB,      -- Renamed from "recurrenceRule"
        "rt1_instanceCreationStartDate"     INTEGER,   -- Renamed from "instanceCreationStartDate", REAL -> INTEGER
        "rt1_instanceCreationPaused"        INTEGER,   -- Renamed from "instanceCreationPaused"
        "rt1_instanceCreationCount"         INTEGER,   -- Renamed from "instanceCreationCount"
        "rt1_afterCompletionReferenceDate"  INTEGER,   -- Renamed from "afterCompletionReferenceDate", REAL -> INTEGER
        "rt1_nextInstanceStartDate"         INTEGER,   -- Renamed from "nextInstanceStartDate", REAL -> INTEGER

        "experimental"                      BLOB,

        "repeater"                          BLOB,
        "repeaterMigrationDate"             REAL
    )
```

### ۳.۳ جدول‌های مخصوص iOS (اختیاری)

این‌ها فقط برای Spotlight و اکستنشن iOS هستند. در اندروید لازم نیستند؛ اگر خواستی فایل کاملاً هم‌شکل باشد، خالی بسازشان.

```sql
CREATE TABLE BSSpotlightDirtyEntity (

    "entityType"        INTEGER NOT NULL,
    "id"                BLOB NOT NULL,
    "changeToken"       BLOB NOT NULL,
    "expansionCursor"   BLOB,

    PRIMARY KEY ("entityType", "id")

) WITHOUT ROWID
-- @@
CREATE TABLE BSSpotlightIndexState (

    "id"                          INTEGER PRIMARY KEY CHECK ("id" = 1),
    "updateState"                 INTEGER NOT NULL,
    "isEnabled"                   INTEGER NOT NULL DEFAULT 0,
    "wholeIndexUpdateToken"       BLOB NOT NULL,
    "wholeIndexExpansionCursor"   BLOB,
    "staticEntitiesUpdateToken"   BLOB,
    "lastCheckpointID"            BLOB
)
-- @@
CREATE TABLE "ThingsTouch_ExtensionCommandStore_Commands" (
"id" INTEGER PRIMARY KEY AUTOINCREMENT,
"type" TEXT,
"body" BLOB
)
-- @@
CREATE TABLE "ThingsTouch_ExtensionCommandStore_Meta" (
"key" TEXT PRIMARY KEY,
"value" BLOB
)
```

### ۳.۴ ایندکس‌ها

```sql
CREATE INDEX "index_BSSpotlightDirtyEntity_expansionCursor"
    ON BSSpotlightDirtyEntity ("entityType", "id")
    WHERE "expansionCursor" IS NOT NULL
-- @@
CREATE INDEX index_TMAreaTag_areas ON TMAreaTag (areas)
-- @@
CREATE INDEX index_TMChecklistItem_task ON TMChecklistItem (task)
-- @@
CREATE INDEX index_TMTaskTag_tasks ON TMTaskTag (tasks)
-- @@
CREATE INDEX index_TMTask_area ON TMTask(area)
-- @@
CREATE INDEX index_TMTask_heading ON TMTask(heading)
-- @@
CREATE INDEX index_TMTask_id_where_recurrenceRuleNotNull
    ON TMTask (uuid)
    WHERE rt1_recurrenceRule IS NOT NULL
-- @@
CREATE INDEX index_TMTask_project ON TMTask(project)
-- @@
CREATE INDEX index_TMTask_repeatingTemplate_and_creationDate
    ON TMTask(rt1_repeatingTemplate, creationDate)
-- @@
CREATE INDEX index_TMTask_stopDate ON TMTask(stopDate)
-- @@
CREATE INDEX index_TMTask_userModificationDate ON TMTask(userModificationDate)
-- @@
CREATE INDEX index_TMTombstone_deletedObjectUUID ON TMTombstone (deletedObjectUUID)
```

نکته: اگر `BSSpotlightDirtyEntity` را نسازی، ایندکس اولش را هم نساز.

## ۴. دیکشنری ستون‌ها و مقادیر

### ۴.۱ مدل موجودیت‌ها

تسک، پروژه و heading همگی ردیف‌های `TMTask` هستند و با `type` از هم جدا می‌شوند. area جدول جدا دارد.

| `type` | معنی | شمار در فایل | ستون‌های رابطه |
| --- | --- | --- | --- |
| 0 | تسک (To-Do) | 650 | `project` (279 ردیف)، `heading` (356 ردیف) |
| 1 | پروژه | 11 | `area` (11 ردیف) |
| 2 | Heading داخل پروژه | 28 | `project` (28 ردیف) |

این نگاشت از شمارش روابط تأیید شده است: همه‌ی ارجاع‌های `TMTask.project` (307 مورد) به ردیف `type=1` می‌روند، همه‌ی ارجاع‌های `heading` (356 مورد) به `type=2` و هر 11 ارجاع `area` به `TMArea`. \[تأییدشده\]

سلسله‌مراتب: `TMArea` ← پروژه (`area`) ← heading (`project`) ← تسک (`heading` و/یا `project`).

### ۴.۲ مقادیر enum

| ستون | مقادیر دیده‌شده | معنی |
| --- | --- | --- |
| `status` | 0 (185)، 3 (504) | 0 = باز، 3 = انجام‌شده \[تأییدشده از رفتار `stopDate`\]. مقدار لغوشده (احتمالاً 2) در نمونه نیست \[حدس\] |
| `start` | 1 (670)، 2 (19) | 1 = Anytime، 2 = Someday \[حدس از نام‌گذاری\]. 0 احتمالاً Inbox است ولی دیده نشد \[حدس\] |
| `startBucket` | همه 0 | احتمالاً 1 برای «Evening» \[حدس\] |
| `trashed` | همه 0 | 1 = در سطل زباله |
| `notesSync` | همه 1 | ثابت |
| `leavesTombstone` | 0 (406)، 1 (283) | ببین بخش ۷ |
| `TMChecklistItem.status` | 0 (90)، 3 (179) | همان معنی `status` تسک |

قاعده‌ی تأییدشده: وقتی `status = 0` است `stopDate` همیشه `NULL` است (185 از 185)، و وقتی `status = 3` است همیشه مقدار دارد (504 از 504).

### ۴.۳ نوع داده و فرمت ستون‌های ویژه

| ستون | نوع واقعی در فایل | توضیح |
| --- | --- | --- |
| `creationDate`, `userModificationDate`, `stopDate`, `usedDate`, `deletionDate` | REAL | ثانیه‌ی Unix با اعشار (مثلاً `1770943871.024922`) |
| `startDate`, `deadline`, `deadlineSuppressionDate`, `todayIndexReferenceDate` | INTEGER | تاریخ فشرده، ببین بخش ۵ |
| `reminderTime` | INTEGER | ساعت و دقیقه‌ی فشرده، ببین بخش ۵ |
| `index`, `todayIndex` | INTEGER | کلید ترتیب؛ مقدارهای منفی بزرگ مثل `-216`. موقع جابه‌جایی کل لیست را renumber نکن \[حدس\] |
| `notes` | TEXT | در این نمونه همه‌ی مقدارها متن ساده‌اند |
| `cachedTags` | BLOB | خالی (`x''`) برای 567 تسک، 16 بایت برای 122 تسک. همین 122 با تعداد ردیف‌های `TMTaskTag` می‌خواند، پس احتمالاً کش تگ‌هاست. فرمت رمزگشایی نشده؛ دست نزن \[حدس\] |
| `rt1_recurrenceRule` | BLOB (XML plist) | ببین بخش ۴.۴ |

### ۴.۴ تکرار شونده‌ها (Repeating)

- دو تسک با `rt1_recurrenceRule` غیرخالی (الگو) و 49 تسک با `rt1_repeatingTemplate` پر (نمونه‌های ساخته‌شده از الگو) وجود دارد. \[تأییدشده\]
- محتوای `rt1_recurrenceRule` یک XML plist به‌شکل `dict` با کلیدهای `ed`، `fa`، `fu`، `ia`، `of`، `rc`، `rrv`، `sr`، `tp`، `ts` است (مثلاً `of` آرایه‌ای از `{wd: 5}` است). \[تأییدشده\]
- معنی دقیق هر کلید مستند نیست. این blob را **بایت‌به‌بایت** ذخیره و برگردان و مقدارش را خودت تفسیر نکن مگر لازم شود.

### ۴.۵ جدول‌های کمکی

- `TMTag.parent` ارجاع به همان جدول است (تگ تو در تو). نمونه: Priority ← Important و ASAP؛ Wishlist ← Buy Next. \[تأییدشده\]
- `TMTaskTag(tasks, tags)` و `TMAreaTag(areas, tags)` جدول رابط‌اند، بدون PK و بدون UNIQUE.
- `Meta` مقدارها را به‌صورت XML plist نگه می‌دارد (`<true/>` یا `<integer>30</integer>`). مقدار `databaseVersion` همان **۳۰** است.
- `TMMetaItem` و `BSSyncronyMetadata` مقدارهای binary plist یا JSON دارند. این‌ها وضعیت موتور سینک‌اند؛ اپ خودت نباید دستکاری‌شان کند.
- `TMSettings` یک ردیف دارد (`logInterval`, `manualLogDate`, `groupTodayByParent`, `uriSchemeAuthenticationToken`). توکن را هرگز در گیت نگذار.

## ۵. رمزگذاری تاریخ و ساعت

تاریخ‌های کاربرمحور (startDate و deadline) به‌صورت یک Int فشرده ذخیره می‌شوند:

- تاریخ: `year << 16 | month << 12 | day << 7`
- ساعت: `hour << 26 | minute << 20`

تست روی داده‌ی واقعی: `132814464` برابر 2026-09-13، و `536870912` برابر 08:00 است. \[تأییدشده\] این Int مستقل از timezone است (فقط روز تقویمی را ذخیره می‌کند)، پس همیشه `LocalDate` و `LocalTime` استفاده کن، نه `Instant`.

```kotlin
package com.artaherzadeh.things.data.db

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object ThingsDate {
    fun packDate(d: LocalDate): Int =
        (d.year shl 16) or (d.monthValue shl 12) or (d.dayOfMonth shl 7)

    fun unpackDate(v: Int): LocalDate =
        LocalDate.of(v ushr 16, (v shr 12) and 0xF, (v shr 7) and 0x1F)

    fun packTime(t: LocalTime): Int =
        (t.hour shl 26) or (t.minute shl 20)

    fun unpackTime(v: Int): LocalTime =
        LocalTime.of(v ushr 26, (v shr 20) and 0x3F)

    // ستون‌های REAL: ثانیه‌ی Unix با اعشار
    fun toEpoch(i: Instant): Double = i.epochSecond + i.nano / 1_000_000_000.0
    fun fromEpoch(d: Double): Instant {
        val s = Math.floor(d).toLong()
        return Instant.ofEpochSecond(s, ((d - s) * 1_000_000_000).toLong())
    }
}
```

## ۶. راه‌اندازی دیتابیس در اندروید

```kotlin
package com.artaherzadeh.things.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ThingsDb(private val ctx: Context) :
    SQLiteOpenHelper(ctx, "main.sqlite", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false) // فایل مرجع FK ندارد
        db.enableWriteAheadLogging()              // فایل مرجع WAL است
    }

    override fun onCreate(db: SQLiteDatabase) {
        val sql = ctx.assets.open("schema.sql").bufferedReader().readText()
        db.beginTransaction()
        try {
            sql.split(Regex("(?m)^-- @@\\s*$"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { db.execSQL(it) }
            // مقدار Meta دقیقاً مثل فایل مرجع
            val plistHead = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
                "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
            db.execSQL("INSERT INTO Meta(key,value) VALUES('databaseVersion', ?)",
                arrayOf(plistHead + "<plist version=\"1.0\"><integer>30</integer></plist>"))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        // مهاجرت‌ها را با ALTER TABLE بنویس و Meta.databaseVersion را هم به‌روز کن
    }
}
```

انحراف شناخته‌شده: `SQLiteOpenHelper` مقدار `PRAGMA user_version` را برابر `version` (یعنی ۱) می‌گذارد، در حالی که فایل مرجع صفر است. این اهمیتی ندارد چون نسخه‌ی واقعی در `Meta.databaseVersion` است. اگر عین‌به‌عین مهم است، فایل را مستقیم با `SQLiteDatabase.openOrCreateDatabase` باز کن و `user_version` را دست‌کاری نکن.

### ۶.۱ مدل‌های داده (نمونه)

برای هر ستون nullable بودنش را حفظ کن، چون در فایل مرجع بیشتر ستون‌ها NULL دارند.

```kotlin
enum class TaskType(val raw: Int) { TODO(0), PROJECT(1), HEADING(2);
    companion object { fun of(v: Int) = entries.first { it.raw == v } } }

enum class TaskStatus(val raw: Int) { OPEN(0), COMPLETED(3);
    companion object { fun of(v: Int) = entries.firstOrNull { it.raw == v } ?: OPEN } }

data class TaskRow(
    val uuid: String,
    val type: TaskType,
    val status: Int,                 // خام نگه دار تا مقدارهای ناشناخته از بین نروند
    val trashed: Boolean,
    val title: String?,
    val notes: String?,
    val creationDate: Double?,
    val userModificationDate: Double?,
    val stopDate: Double?,
    val start: Int?,
    val startDate: Int?,             // فشرده
    val deadline: Int?,              // فشرده
    val reminderTime: Int?,          // فشرده
    val index: Int?,
    val todayIndex: Int?,
    val area: String?,
    val project: String?,
    val heading: String?,
    val leavesTombstone: Boolean,
)
```

برای ستون‌هایی که الان استفاده نمی‌کنی (مثل `rt1_*` و `cachedTags`) هم هنگام آپدیت، **فقط ستون‌هایی را که لازم است** در `UPDATE` بنویس، تا مقدار ناشناخته‌ی بقیه دست‌نخورده بماند.

## ۷. قواعد نوشتن (برای اینکه چیزی خراب نشود)

این‌ها از الگوی داده و ساختار جدول‌ها استنباط شده‌اند. \[حدس\] قبل از تکیه کردن، با فایل واقعی تست‌شان کن.

1. **uuid:** ۲۲ کاراکتر الفبایی-عددی. برای ایمنی قبل از نوشتن، فایل مرجع را با این کوئری چک کن که فقط همین نویسه‌ها هستند: `SELECT uuid FROM TMTask WHERE uuid GLOB '*[^A-Za-z0-9]*';` و بعد تولیدکننده‌ات را با همان الفبا بساز.
2. **`userModificationDate`:** بعد از هر تغییر کاربر، روی تسک (و چک‌لیست) به‌روز شود.
3. **شمارنده‌های denormalized:** ستون‌های `untrashedLeafActionsCount`، `openUntrashedLeafActionsCount`، `checklistItemsCount` و `openChecklistItemsCount` کش‌اند. بعد از اضافه، حذف یا تغییر وضعیت ردیف‌های وابسته، باید دوباره حساب شوند. معنی دقیقشان را با چند پروژه‌ی واقعی در فایل مرجع بسنج (مثلاً شمارش دستی با `COUNT`).
4. **`cachedTags`:** هنگام تغییر `TMTaskTag` همگام شود. تا زمانی که فرمتش را رمزگشایی نکرده‌ای، تگ را فقط از `TMTaskTag` بخوان و `cachedTags` را دست نزن.
5. **حذف و tombstone:** در فایل 191 رکورد `TMTombstone(uuid, deletionDate, deletedObjectUUID)` هست و 283 تسک و 72 چک‌لیست `leavesTombstone = 1` دارند. فرضیه: با حذف ردیفی که این فلگ را دارد، باید یک رکورد tombstone با `deletedObjectUUID` همان ردیف ثبت شود تا سینک از حذف خبردار شود.
6. **تکرارشونده‌ها:** هنگام تکمیل یک نمونه، تولید نمونه‌ی بعدی را با ستون‌های `rt1_*` انجام بده. تا وقتی معنی کلیدهای plist را نمی‌دانی، فقط نمونه‌های موجود را نمایش بده و الگو را ویرایش نکن.
7. **ترتیب (`index`):** برای درج بین دو آیتم، عددی بین دو `index` همسایه بگیر. اگر فاصله تمام شد، فقط همان گروه را renumber کن.
8. **تراکنش:** هر تغییر چندجدولی (مثلاً حذف پروژه + headingها + تسک‌ها + tombstone) در یک تراکنش باشد.

## ۸. کوئری‌های نمونه

همه‌ی این روابط روی فایل مرجع تأیید شده‌اند.

```sql
-- areaها
SELECT uuid, title, "index" FROM TMArea ORDER BY "index";

-- پروژه‌های یک area
SELECT uuid, title FROM TMTask
WHERE type = 1 AND trashed = 0 AND area = :areaUuid
ORDER BY "index";

-- headingهای یک پروژه
SELECT uuid, title FROM TMTask
WHERE type = 2 AND trashed = 0 AND project = :projectUuid
ORDER BY "index";

-- تسک‌های باز زیر یک heading
SELECT * FROM TMTask
WHERE type = 0 AND trashed = 0 AND status = 0 AND heading = :headingUuid
ORDER BY "index";

-- چک‌لیست یک تسک
SELECT uuid, title, status FROM TMChecklistItem
WHERE task = :taskUuid ORDER BY "index";

-- تگ‌های یک تسک
SELECT g.uuid, g.title FROM TMTag g
JOIN TMTaskTag tt ON tt.tags = g.uuid
WHERE tt.tasks = :taskUuid ORDER BY g."index";

-- نمونه‌های یک تسک تکرارشونده
SELECT * FROM TMTask WHERE rt1_repeatingTemplate = :templateUuid
ORDER BY creationDate;
```

توجه: منطق لیست‌های Today، Upcoming و Anytime (ترکیب `start`، `startDate`، `todayIndex` و `todayIndexReferenceDate`) را از روی نمونه‌ی واقعی خودت باز مهندسی کن و در سند بنویس؛ من فقط ستون‌ها را تأیید کرده‌ام، نه الگوریتم دقیق لیست‌ها را.

## ۹. تست و اعتبارسنجی

1. **مقایسه‌ی اسکیما:** بعد از ساخت دیتابیس خالی، خروجی `SELECT type,name,sql FROM sqlite_master ORDER BY type,name` را با فایل مرجع diff کن. باید برای جدول‌ها و ایندکس‌هایی که ساختی یکی باشد.
2. **Round-trip:** فایل مرجع را در `assets` بگذار، با Helper باز کن، یک تسک بساز و دوباره بیرون بیاور. با `sqlite3 out.sqlite .dump` مقایسه کن.
3. **تست واحد تاریخ:** `unpackDate(132814464) == 2026-09-13` و `unpackTime(536870912) == 08:00`، و `pack(unpack(x)) == x`.
4. **ثبات داده:** بعد از هر سناریو `PRAGMA integrity_check;` را اجرا کن و این‌ها را بسنج:
   - هیچ `TMTask.project` یتیمی که به ردیف `type=1` اشاره نکند نباشد.
   - هیچ `heading` یتیمی که به ردیف `type=2` اشاره نکند نباشد.
   - هیچ ردیف `TMTaskTag` با `tasks` یا `tags` ناموجود نباشد.
5. **WAL:** قبل از export گرفتن فایل از گوشی، `PRAGMA wal_checkpoint(TRUNCATE);` را اجرا کن تا همه‌چیز در فایل اصلی باشد.

## ۱۰. سینک و امنیت

- **سینک:** وجود `BSSyncronyMetadata`، `TMTombstone` و `leavesTombstone` نشان می‌دهد این فایل فقط کش محلی یک موتور سینک اختصاصی است. هم‌شکل بودن دیتابیس **تضمین نمی‌کند** سرور Things Cloud تغییرات اپ تو را قبول کند؛ احتمالاً خود فایل سینک نمی‌شود. این پروتکل مستند نیست و من از جزئیاتش خبر ندارم. اگر منظورت از «کلاد» سرویس رسمی Things است، قبل از ساختن چیزی روی آن شرایط استفاده‌اش را بررسی کن. اگر منظورت بک‌اند خودت است، همین الگو (uuid + تاریخ تغییر + tombstone) برای سینک خودت هم کار می‌کند.
- **داده‌های حساس در فایل نمونه:** ایمیل اکانت (در `BSSyncronyMetadata`)، `uriSchemeAuthenticationToken` در `TMSettings`، و همه‌ی تسک‌ها و یادداشت‌هایت. ریپو عمومی است، پس فایل را از ریپو و تاریخچه پاک کن (مثلاً `git filter-repo`) و توکن را در Things عوض کن. برای تست، یک فایل با داده‌ی ساختگی بساز.
- **ردیف‌های `BSSyncronyMetadata`/`TMSettings`:** در اپ جدیدت از فایل مرجع کپی نکن؛ با مقدار خالی یا ساختگی بساز.

## ۱۱. چک‌لیست قبل از شروع

- [ ] `schema.sql` در `assets` و با جداکننده‌ی `-- @@`.
- [ ] `ThingsDb` با WAL و بدون FK.
- [ ] `ThingsDate` با تست واحد.
- [ ] مدل‌های `TaskRow` و غیره با ستون‌های nullable.
- [ ] `Meta.databaseVersion = 30` ساخته می‌شود.
- [ ] diff اسکیما با فایل مرجع صفر است.
- [ ] فایل مرجع و توکن از گیت پاک شده‌اند.
- [ ] سوال‌های باز (بخش ۱۲) بررسی شده‌اند.

## ۱۲. موارد باز (باید خودت تأیید کنی)

1. مقدار enum برای `status = 2` (لغوشده) و `start = 0` (Inbox).
2. معنی `startBucket = 1`.
3. فرمت دقیق `cachedTags`.
4. معنی دقیق کلیدهای plist در `rt1_recurrenceRule`.
5. معنی دقیق چهار شمارنده‌ی `*Count` و قاعده‌ی به‌روزرسانی‌شان.
6. رفتار دقیق tombstone هنگام حذف و محتوای `BSSyncronyMetadata`.
7. الگوریتم دقیق لیست‌های Today/Upcoming/Anytime.
