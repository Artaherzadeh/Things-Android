# گزارش کاری دیتابیس — ۷ نوامبر ۲۰۲۶

این گزارش فقط بخش **دیتابیس** را پوشش می‌دهد؛ UI و تغییرات ظاهری امروز جزو این گزارش نیستند. کارها روی شاخه `main` مخزن `Artaherzadeh/Things-Android` انجام و commit شده‌اند.

## فایل‌های ایجادشده

| فایل | مسیر | نقش |
|---|---|---|
| `schema.sql` | `app/src/main/assets/schema.sql` | اسکیمای SQLite به شکل Things؛ جدول‌ها و indexها؛ بدون جدول‌های iOS-only. |
| `ThingsDb.kt` | `app/src/main/java/com/things/app/data/db/ThingsDb.kt` | SQLiteOpenHelper، فعال‌سازی WAL، ساخت schema، تنظیم `Meta.databaseVersion = 30`. |
| `SchemaSql.kt` | `app/src/main/java/com/things/app/data/db/SchemaSql.kt` | helperهای خالص برای split کردن schema و ساخت `databaseVersionPlist`. |
| `ThingsDate.kt` | `app/src/main/java/com/things/app/data/db/ThingsDate.kt` | codec تاریخ و زمان به فرمت packed Things. |
| `DbModels.kt` | `app/src/main/java/com/things/app/data/db/DbModels.kt` | مدل‌های دیتابیس و snapshot. |
| `ThingsReadRepository.kt` | `app/src/main/java/com/things/app/data/db/ThingsReadRepository.kt` | خواندن snapshot از دیتابیس. |
| `ThingsUiReader.kt` | `app/src/main/java/com/things/app/data/db/ThingsUiReader.kt` | تبدیل snapshot به مدل UI. |
| `ThingsListReader.kt` | `app/src/main/java/com/things/app/data/db/ThingsListReader.kt` | خواندن لیست‌ها و دسته‌بندی system list. |
| `ThingsWriteRepository.kt` | `app/src/main/java/com/things/app/data/db/ThingsWriteRepository.kt` | نوشتن‌های transactional برای task/project/heading/area/checklist/tag. |
| `ThingsEntityOperations.kt` | `app/src/main/java/com/things/app/data/db/ThingsEntityOperations.kt` | عملیات entity-level مثل duplicate/convert/restore/ordering. |
| `ThingsMoveRepository.kt` | `app/src/main/java/com/things/app/data/db/ThingsMoveRepository.kt` | جابه‌جایی task/project/heading بین containerها. |
| `RepeatStorageReader.kt` | `app/src/main/java/com/things/app/data/db/RepeatStorageReader.kt` | خواندن repeat template و instance از دیتابیس. |
| `RepeatPlistInspector.kt` | `app/src/main/java/com/things/app/data/db/RepeatPlistInspector.kt` | inspect کردن XML plist بدون تغییر بایت اصلی. |
| `RepeatRuleDecoder.kt` | `app/src/main/java/com/things/app/data/db/RepeatRuleDecoder.kt` | تبدیل inspection به مدل typed. |

## فایل‌های تست ایجادشده

| فایل | مسیر | نقش |
|---|---|---|
| `SchemaTest.kt` | `app/src/test/java/com/things/app/data/db/SchemaTest.kt` | تست split شدن schema به ۲۴ CREATE. |
| `ThingsDateTest.kt` | `app/src/test/java/com/things/app/data/db/ThingsDateTest.kt` | تست codec تاریخ و زمان. |
| `ThingsListReaderTest.kt` | `app/src/test/java/com/things/app/data/db/ThingsListReaderTest.kt` | تست classification و read projection. |
| `ThingsWriteRepositoryTest.kt` | `app/src/test/java/com/things/app/data/db/ThingsWriteRepositoryTest.kt` | تست عملیات نوشتن و stale checklist. |
| `ThingsEntityOperationsTest.kt` | `app/src/test/java/com/things/app/data/db/ThingsEntityOperationsTest.kt` | تست duplicate/convert/restore/ordering. |
| `ThingsMoveRepositoryTest.kt` | `app/src/test/java/com/things/app/data/db/ThingsMoveRepositoryTest.kt` | تست move بین containerها. |
| `RepeatStorageReaderTest.kt` | `app/src/test/java/com/things/app/data/db/RepeatStorageReaderTest.kt` | تست خواندن repeat storage. |
| `RepeatPlistInspectorTest.kt` | `app/src/test/java/com/things/app/data/db/RepeatPlistInspectorTest.kt` | ۱۳ تست inspect کردن plist. |
| `RepeatRuleDecoderTest.kt` | `app/src/test/java/com/things/app/data/db/RepeatRuleDecoderTest.kt` | ۸ تست decode typed. |

## کارهای انجام‌شده به ترتیب

### ۱. schema و SQLite helper
- ساخت `schema.sql` با جدول‌ها و indexهای Things-shaped.
- ساخت `ThingsDb` با WAL و version 30.
- ساخت `SchemaSql` برای split کردن schema.
- تست split شدن schema به ۲۴ CREATE.

### ۲. تاریخ و زمان
- ساخت `ThingsDate` برای encode/decode تاریخ و زمان.
- تست unit برای codec.

### ۳. snapshot و read mapping
- ساخت `DbModels` برای مدل‌های دیتابیس.
- ساخت `ThingsReadRepository` برای خواندن snapshot.
- ساخت `ThingsUiReader` برای تبدیل به مدل UI.
- ساخت `ThingsListReader` برای دسته‌بندی system list.

### ۴. write repository
- ساخت `ThingsWriteRepository` با transaction.
- پشتیبانی از create/rename/patch/complete/trash.
- پشتیبانی از checklist و tag.
- guard در برابر stale checklist و تغییر ناخواسته subtree.

### ۵. entity operations
- duplicate و convert و restore و ordering.
- حفظ schedule/deadline/tags/repeat هنگام convert.

### ۶. move repository
- جابه‌جایی task/project/heading بین containerها.
- حفظ ترتیب و جلوگیری از stale state.

### ۷. system list classification
- unify کردن classification و read projection.
- تست regression برای classification.

### ۸. repeat storage inspection
- خواندن repeat template و instance.
- حفظ بایت اصلی و جلوگیری از infer کردن repeat mode.

### ۹. plist inspection
- inspect کردن XML plist به‌صورت lossless.
- حفظ کلیدهای ناشناخته و بایت اصلی.
- ۱۳ تست برای امنیت و دقت.

### ۱۰. typed decode
- تبدیل inspection به مدل typed.
- حفظ unknown key و nested structure.
- ۸ تست برای انواع مختلف.

## کارهایی که هنوز انجام نشده

- repeat engine واقعی (محاسبه next occurrence و ساخت instance)
- sync layer و pending op
- integration test کامل برای CRUD و move
- اتصال repository به ViewModel و UI (فعلاً نمی‌خواهی)

## وضعیت کلی

لایه دیتابیس الان شامل schema، read، write، move، entity operation، repeat storage inspection و typed decode است. چیزی که مانده، تکمیل repeat engine و sync و integration test است.
