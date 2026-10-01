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
  private TextView title;
  private TextView subtitle;
  private TextView empty;
  private Adapter adapter;
  private AlumniSheet.Row row;
  private boolean actionsBound;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_match);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    title = findViewById(R.id.title);
    subtitle = findViewById(R.id.subtitle);
    empty = findViewById(R.id.empty);
    RecyclerView list = findViewById(R.id.list);
    adapter = new Adapter(picked -> {
      AlumniSheet.Row chosen = row;
      if (chosen == null) {
        return;
      }
      AlumniPending.drop(this, chosen);
      AppExecutors.disk().execute(() -> ScheduleCoordinator.pairAlumni(this, chosen, picked.id));
      Toast.makeText(this, R.string.alumni_paired, Toast.LENGTH_LONG).show();
      finish();
    });
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);

    int index = getIntent().getIntExtra(ExtraKeys.PENDING_INDEX, -1);
    row = extraRow();
    if (row == null && AlumniPending.cached()) {
      row = pick(AlumniPending.load(this), index);
    }
    if (row != null) {
      paintSheet(row);
      loadSuggestions(row);
      return;
    }
    empty.setVisibility(View.VISIBLE);
    empty.setText(R.string.alumni_pair_wait);
    AppExecutors.query().execute(() -> {
      AlumniSheet.Row found = pick(AlumniPending.load(this), index);
      AppExecutors.main(() -> {
        if (found == null) {
          finish();
          return;
        }
        row = found;
        paintSheet(found);
        loadSuggestions(found);
      });
    });
  }

  @SuppressWarnings("deprecation")
  private AlumniSheet.Row extraRow() {
    Object extra = getIntent().getSerializableExtra(ExtraKeys.PENDING_ROW);
    return extra instanceof AlumniSheet.Row ? (AlumniSheet.Row) extra : null;
  }

  private static AlumniSheet.Row pick(List<AlumniSheet.Row> pending, int index) {
    if (pending == null || index < 0 || index >= pending.size()) {
      return null;
    }
    return pending.get(index);
  }

  private void paintSheet(AlumniSheet.Row sheet) {
    title.setText(sheet.displayName());
    String sheetDay = sheet.birthMonth > 0
      ? DateUtils.monthDayLabel(sheet.birthMonth, sheet.birthDay)
      : getString(R.string.alumni_no_sheet_birthday);
    String phone = AlumniDesk.nigeriaDigits(sheet.phone).length() == 13
      ? AlumniDesk.displayPhone(sheet.phone)
      : getString(R.string.alumni_no_number_short);
    subtitle.setText(getString(R.string.alumni_pair_sheet, sheetDay, phone));
    adapter.setSheetDay(sheetDay);
    empty.setVisibility(View.VISIBLE);
    empty.setText(R.string.alumni_pair_wait);
    findViewById(R.id.pair_actions).setVisibility(View.VISIBLE);
    if (actionsBound) {
      return;
    }
    actionsBound = true;
    findViewById(R.id.btn_new_card).setOnClickListener(v -> {
      AlumniSheet.Row chosen = row;
      if (chosen == null) {
        return;
      }
      AlumniPending.drop(this, chosen);
      AppExecutors.disk().execute(() -> ScheduleCoordinator.addAlumniFromSheet(this, chosen));
      Toast.makeText(this, R.string.alumni_pair_added, Toast.LENGTH_LONG).show();
      finish();
    });
    findViewById(R.id.btn_drop).setOnClickListener(v -> {
      AlumniSheet.Row chosen = row;
      if (chosen == null) {
        return;
      }
      AlumniPending.drop(this, chosen);
      AppExecutors.disk().execute(() -> ScheduleCoordinator.dropPendingRow(this, chosen));
      finish();
    });
  }

  private void loadSuggestions(AlumniSheet.Row sheet) {
    String sheetDay = sheet.birthMonth > 0
      ? DateUtils.monthDayLabel(sheet.birthMonth, sheet.birthDay)
      : getString(R.string.alumni_no_sheet_birthday);
    if (AlumniMatch.hasRoster()) {
      showSuggestions(AlumniMatch.suggest(sheet, 8), sheetDay);
      return;
    }
    AppExecutors.query().execute(() -> {
      List<Member> roster = AppDatabase.get(this).memberDao().getAllSync();
      AlumniMatch.rememberRoster(roster);
      List<AlumniMatch.Suggestion> suggestions = AlumniMatch.suggest(sheet, 8);
      AppExecutors.main(() -> showSuggestions(suggestions, sheetDay));
    });
  }

  private void showSuggestions(List<AlumniMatch.Suggestion> suggestions, String sheetDay) {
    adapter.replace(suggestions, sheetDay);
    empty.setText(R.string.alumni_pair_no_suggestions);
    empty.setVisibility(suggestions == null || suggestions.isEmpty() ? View.VISIBLE : View.GONE);
  }

  static class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    interface Listener {
      void onPair(Member member);
    }

    private final List<AlumniMatch.Suggestion> rows = new ArrayList<>();
    private final Listener listener;
    private String sheetDay = "";

    Adapter(Listener listener) {
      this.listener = listener;
    }

    void setSheetDay(String sheetDay) {
      this.sheetDay = sheetDay == null ? "" : sheetDay;
    }

    void replace(List<AlumniMatch.Suggestion> next, String sheetDay) {
      setSheetDay(sheetDay);
      rows.clear();
      if (next != null) {
        rows.addAll(next);
      }
      notifyDataSetChanged();
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
