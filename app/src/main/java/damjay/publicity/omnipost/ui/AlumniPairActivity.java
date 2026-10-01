package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.AlumniMatch;
import damjay.publicity.omnipost.scheduler.AlumniPending;
import damjay.publicity.omnipost.scheduler.AlumniSheet;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import java.util.ArrayList;
import java.util.List;

public class AlumniPairActivity extends AppCompatActivity {
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_match);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    TextView title = findViewById(R.id.title);
    TextView subtitle = findViewById(R.id.subtitle);
    TextView empty = findViewById(R.id.empty);
    RecyclerView list = findViewById(R.id.list);
    int index = getIntent().getIntExtra(ExtraKeys.PENDING_INDEX, -1);
    AppExecutors.disk().execute(() -> {
      List<AlumniSheet.Row> pending = AlumniPending.load(this);
      if (index < 0 || index >= pending.size()) {
        AppExecutors.main(this::finish);
        return;
      }
      AlumniSheet.Row row = pending.get(index);
      List<Member> roster = AppDatabase.get(this).memberDao().getAllSync();
      List<AlumniMatch.Suggestion> suggestions = AlumniMatch.suggest(row, roster, 8);
      AppExecutors.main(() -> {
        title.setText(row.displayName());
        String sheetDay = row.birthMonth > 0
          ? DateUtils.monthDayLabel(row.birthMonth, row.birthDay)
          : getString(R.string.alumni_no_sheet_birthday);
        String phone = AlumniDesk.nigeriaDigits(row.phone).length() == 13
          ? AlumniDesk.displayPhone(row.phone)
          : getString(R.string.alumni_no_number_short);
        subtitle.setText(getString(R.string.alumni_pair_sheet, sheetDay, phone));
        Adapter adapter = new Adapter(suggestions, sheetDay, picked -> {
          AppExecutors.disk().execute(() -> {
            ScheduleCoordinator.pairAlumni(this, row, picked.id);
            AppExecutors.main(() -> {
              Toast.makeText(this, R.string.alumni_paired, Toast.LENGTH_LONG).show();
              finish();
            });
          });
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        empty.setVisibility(suggestions.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(R.string.alumni_pair_no_suggestions);
        View actions = findViewById(R.id.pair_actions);
        actions.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_new_card).setOnClickListener(v -> AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.addAlumniFromSheet(this, row);
          AppExecutors.main(() -> {
            Toast.makeText(this, R.string.alumni_pair_added, Toast.LENGTH_LONG).show();
            finish();
          });
        }));
        findViewById(R.id.btn_drop).setOnClickListener(v -> AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.dropPendingRow(this, row);
          AppExecutors.main(this::finish);
        }));
      });
    });
  }

  static class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    interface Listener {
      void onPair(Member member);
    }

    private final List<AlumniMatch.Suggestion> rows;
    private final String sheetDay;
    private final Listener listener;

    Adapter(List<AlumniMatch.Suggestion> rows, String sheetDay, Listener listener) {
      this.rows = rows == null ? new ArrayList<>() : rows;
      this.sheetDay = sheetDay == null ? "" : sheetDay;
      this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.item_alumni_suggestion, parent, false);
      return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      holder.bind(rows.get(position), sheetDay, listener);
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
      private final TextView name;
      private final TextView meta;
      private final MaterialButton pair;

      Holder(View itemView) {
        super(itemView);
        name = itemView.findViewById(R.id.name);
        meta = itemView.findViewById(R.id.meta);
        pair = itemView.findViewById(R.id.btn_pair);
      }

      void bind(AlumniMatch.Suggestion item, String sheetDay, Listener listener) {
        Member member = item.roster;
        name.setText(member.name);
        String rosterDay = member.birthMonth > 0
          ? DateUtils.monthDayLabel(member.birthMonth, member.birthDay)
          : itemView.getContext().getString(R.string.alumni_no_sheet_birthday);
        String compare;
        if (item.birthdaySame) {
          compare = itemView.getContext().getString(R.string.alumni_bday_same, rosterDay, sheetDay);
        } else if (item.birthdayClash) {
          compare = itemView.getContext().getString(R.string.alumni_bday_clash, rosterDay, sheetDay);
        } else {
          compare = itemView.getContext().getString(R.string.alumni_bday_roster, rosterDay, sheetDay);
        }
        meta.setText(compare);
        pair.setOnClickListener(v -> listener.onPair(member));
      }
    }
  }
}
