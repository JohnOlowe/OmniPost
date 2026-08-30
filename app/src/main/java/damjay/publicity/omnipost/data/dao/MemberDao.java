package damjay.publicity.omnipost.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import damjay.publicity.omnipost.data.entity.Member;
import java.util.List;

@Dao
public interface MemberDao {
  @Insert
  long insert(Member member);

  @Update
  int update(Member member);

  @Query("SELECT * FROM members ORDER BY birthMonth ASC, birthDay ASC, name ASC")
  LiveData<List<Member>> observeAll();

  @Query("SELECT * FROM members ORDER BY birthMonth ASC, birthDay ASC")
  List<Member> getAllSync();

  @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
  Member getById(long id);

  @Query("DELETE FROM members WHERE id = :id")
  int deleteById(long id);
}
