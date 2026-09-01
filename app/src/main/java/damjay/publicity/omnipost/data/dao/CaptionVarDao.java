package damjay.publicity.omnipost.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import java.util.List;

@Dao
public interface CaptionVarDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  long insert(CaptionVar item);

  @Update
  int update(CaptionVar item);

  @Query("SELECT * FROM caption_vars WHERE id = :id LIMIT 1")
  CaptionVar getById(long id);

  @Query("SELECT * FROM caption_vars WHERE name = :name LIMIT 1")
  CaptionVar findByName(String name);

  @Query("SELECT * FROM caption_vars ORDER BY name COLLATE NOCASE ASC")
  LiveData<List<CaptionVar>> observeAll();

  @Query("SELECT * FROM caption_vars ORDER BY name COLLATE NOCASE ASC")
  List<CaptionVar> getAllSync();

  @Query("DELETE FROM caption_vars WHERE id = :id")
  int deleteById(long id);
}
