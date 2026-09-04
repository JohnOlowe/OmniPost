package damjay.publicity.omnipost.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import damjay.publicity.omnipost.data.entity.Draft;
import java.util.List;

@Dao
public interface DraftDao {
  @Insert
  long insert(Draft draft);

  @Update
  int update(Draft draft);

  @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
  LiveData<List<Draft>> observeAll();

  @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
  Draft getById(long id);

  @Query("SELECT * FROM drafts WHERE taskId = :taskId LIMIT 1")
  Draft findByTaskId(long taskId);

  @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
  List<Draft> getAllSync();

  @Query("DELETE FROM drafts WHERE id = :id")
  int deleteById(long id);
}
