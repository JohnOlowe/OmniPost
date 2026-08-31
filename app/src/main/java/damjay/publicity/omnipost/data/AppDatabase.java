package damjay.publicity.omnipost.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import damjay.publicity.omnipost.data.dao.DraftDao;
import damjay.publicity.omnipost.data.dao.MemberDao;
import damjay.publicity.omnipost.data.dao.TaskDao;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;

@Database(
  entities = {Task.class, Draft.class, Member.class},
  version = 3,
  exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
  public abstract TaskDao taskDao();

  public abstract DraftDao draftDao();

  public abstract MemberDao memberDao();

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

  private static volatile AppDatabase INSTANCE;

  public static AppDatabase get(Context context) {
    if (INSTANCE == null) {
      synchronized (AppDatabase.class) {
        if (INSTANCE == null) {
          INSTANCE = Room.databaseBuilder(
              context.getApplicationContext(),
              AppDatabase.class,
              "omnipost.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build();
        }
      }
    }
    return INSTANCE;
  }
}
