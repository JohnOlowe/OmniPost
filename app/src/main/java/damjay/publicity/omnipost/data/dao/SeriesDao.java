package damjay.publicity.omnipost.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import damjay.publicity.omnipost.data.entity.Series;
import java.util.List;

@Dao
public interface SeriesDao {
  @Insert
  long insert(Series series);

  @Update
  int update(Series series);

  @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
  Series getById(long id);

  @Query("SELECT * FROM series WHERE seedKey = :seedKey LIMIT 1")
  Series findBySeed(String seedKey);

  @Query("SELECT * FROM series WHERE enabled = 1 ORDER BY id ASC")
  List<Series> getEnabledSync();

  @Query("SELECT * FROM series ORDER BY id ASC")
  LiveData<List<Series>> observeAll();

  @Query("SELECT * FROM series ORDER BY id ASC")
  List<Series> getAllSync();

  @Query("SELECT COUNT(*) FROM series")
  int count();

  @Query("DELETE FROM series WHERE id = :id")
  int deleteById(long id);
}
