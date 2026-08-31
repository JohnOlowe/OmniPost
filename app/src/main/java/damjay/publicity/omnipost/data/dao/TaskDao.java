package damjay.publicity.omnipost.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import damjay.publicity.omnipost.data.entity.Task;
import java.util.List;

@Dao
public interface TaskDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  long insert(Task task);

  @Update
  int update(Task task);

  @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
  Task getById(long id);

  @Query("SELECT * FROM tasks WHERE occurrenceKey = :key LIMIT 1")
  Task findByKey(String key);

  @Query("SELECT * FROM tasks WHERE status != 'POSTED' ORDER BY postAtMillis ASC")
  LiveData<List<Task>> observeActive();

  @Query("SELECT * FROM tasks WHERE status = 'POSTED' ORDER BY postedAtMillis DESC")
  LiveData<List<Task>> observePosted();

  @Query("SELECT * FROM tasks WHERE status != 'POSTED' ORDER BY postAtMillis ASC")
  List<Task> getActiveSync();

  @Query("SELECT * FROM tasks WHERE status != 'POSTED' ORDER BY postAtMillis ASC LIMIT 1")
  Task nextActive();

  @Query("SELECT * FROM tasks WHERE status = 'NAGGING'")
  List<Task> getNaggingSync();

  @Query("SELECT * FROM tasks WHERE memberId = :memberId AND status != 'POSTED'")
  List<Task> getActiveForMember(long memberId);

  @Query("UPDATE tasks SET status = :status WHERE id = :id")
  int updateStatus(long id, String status);

  @Query("UPDATE tasks SET status = 'POSTED', postedAtMillis = :when, snoozeUntilMillis = 0 WHERE id = :id")
  int markPosted(long id, long when);

  @Query("UPDATE tasks SET status = :status, snoozeUntilMillis = :until WHERE id = :id")
  int setSnooze(long id, String status, long until);

  @Query("UPDATE tasks SET linkedDraftId = :draftId WHERE id = :id")
  int setLinkedDraft(long id, long draftId);

  @Query("DELETE FROM tasks WHERE id = :id")
  int deleteById(long id);

  @Query("SELECT * FROM tasks WHERE type = 'TEST' AND postAtMillis < :staleBefore")
  List<Task> staleTests(long staleBefore);

  @Query("DELETE FROM tasks WHERE type = 'TEST' AND postAtMillis < :staleBefore")
  int deleteStaleTests(long staleBefore);
}
