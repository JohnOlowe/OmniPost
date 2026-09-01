package damjay.publicity.omnipost.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import damjay.publicity.omnipost.data.dao.CaptionVarDao;
import damjay.publicity.omnipost.data.dao.DraftDao;
import damjay.publicity.omnipost.data.dao.MemberDao;
import damjay.publicity.omnipost.data.dao.SeriesDao;
import damjay.publicity.omnipost.data.dao.TaskDao;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;

@Database(
  entities = {Task.class, Draft.class, Member.class, Series.class, CaptionVar.class},
  version = 7,
  exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
  public abstract TaskDao taskDao();

  public abstract DraftDao draftDao();

  public abstract MemberDao memberDao();

  public abstract SeriesDao seriesDao();

  public abstract CaptionVarDao captionVarDao();

  static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE tasks ADD COLUMN snoozeUntilMillis INTEGER NOT NULL DEFAULT 0");
    }
  };

  static final Migration MIGRATION_2_3 = new Migration(2, 3) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE tasks ADD COLUMN timesLocked INTEGER NOT NULL DEFAULT 0");
    }
  };

  static final Migration MIGRATION_3_4 = new Migration(3, 4) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL("ALTER TABLE tasks ADD COLUMN seriesId INTEGER NOT NULL DEFAULT 0");
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS post_series ("
          + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
          + "title TEXT NOT NULL, "
          + "kind TEXT NOT NULL, "
          + "caption TEXT NOT NULL, "
          + "eventAtMillis INTEGER NOT NULL, "
          + "postHour INTEGER NOT NULL, "
          + "postMinute INTEGER NOT NULL, "
          + "lastOfPrevMonth INTEGER NOT NULL, "
          + "tenth INTEGER NOT NULL, "
          + "twentieth INTEGER NOT NULL, "
          + "enabled INTEGER NOT NULL, "
          + "seedKey TEXT NOT NULL)");
    }
  };

  static final Migration MIGRATION_4_5 = new Migration(4, 5) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL(
        "ALTER TABLE post_series ADD COLUMN endAtMillis INTEGER NOT NULL DEFAULT 0");
      db.execSQL(
        "ALTER TABLE post_series ADD COLUMN vars TEXT NOT NULL DEFAULT ''");
    }
  };

  static final Migration MIGRATION_5_6 = new Migration(5, 6) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL(
        "ALTER TABLE post_series ADD COLUMN weekdays INTEGER NOT NULL DEFAULT 0");
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS caption_vars ("
          + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
          + "name TEXT NOT NULL, "
          + "label TEXT NOT NULL, "
          + "value TEXT NOT NULL)");
      db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_caption_vars_name ON caption_vars(name)");
    }
  };

  static final Migration MIGRATION_6_7 = new Migration(6, 7) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
      db.execSQL(
        "ALTER TABLE tasks ADD COLUMN captionSavedAt INTEGER NOT NULL DEFAULT 0");
    }
  };

  private static volatile AppDatabase INSTANCE;

  public static AppDatabase get(Context context) {
    if (INSTANCE == null) {
      synchronized (AppDatabase.class) {
        if (INSTANCE == null) {
          INSTANCE = Room.databaseBuilder(
              context.getApplicationContext(),
              AppDatabase.class,
              "omnipost.db")
            .addMigrations(
              MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
              MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build();
        }
      }
    }
    return INSTANCE;
  }
}
