package damjay.publicity.omnipost.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import damjay.publicity.omnipost.data.dao.DraftDao;
import damjay.publicity.omnipost.data.dao.MemberDao;
import damjay.publicity.omnipost.data.dao.TaskDao;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;

@Database(
  entities = {Task.class, Draft.class, Member.class},
  version = 1,
  exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
  public abstract TaskDao taskDao();

  public abstract DraftDao draftDao();

  public abstract MemberDao memberDao();

  private static volatile AppDatabase INSTANCE;

  public static AppDatabase get(Context context) {
    if (INSTANCE == null) {
      synchronized (AppDatabase.class) {
        if (INSTANCE == null) {
          INSTANCE = Room.databaseBuilder(
              context.getApplicationContext(),
              AppDatabase.class,
              "omnipost.db")
            .fallbackToDestructiveMigration()
            .build();
        }
      }
    }
    return INSTANCE;
  }
}
