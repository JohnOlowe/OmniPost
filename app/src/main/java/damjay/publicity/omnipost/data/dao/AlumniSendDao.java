package damjay.publicity.omnipost.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import damjay.publicity.omnipost.data.entity.AlumniSend;
import java.util.List;

@Dao
public interface AlumniSendDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  long upsert(AlumniSend send);

  @Query("SELECT * FROM alumni_sends WHERE yearMonth = :yearMonth")
  LiveData<List<AlumniSend>> observeMonth(int yearMonth);

  @Query("SELECT * FROM alumni_sends WHERE yearMonth = :yearMonth")
  List<AlumniSend> getMonth(int yearMonth);

  @Query(
    "DELETE FROM alumni_sends WHERE memberId = :memberId AND yearMonth = :yearMonth AND kind = :kind")
  int delete(long memberId, int yearMonth, String kind);
}
