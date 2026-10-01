package damjay.publicity.omnipost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.AlumniMatch;
import damjay.publicity.omnipost.scheduler.AlumniPending;
import damjay.publicity.omnipost.scheduler.AlumniSheet;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import java.util.ArrayList;
import java.util.List;

public class AlumniMatchActivity extends AppCompatActivity {
  private final List<AlumniSheet.Row> rows = new ArrayList<>();
  private Adapter adapter;
  private TextView empty;
  private TextView subtitle;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_match);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    empty = findViewById(R.id.empty);
    subtitle = findViewById(R.id.subtitle);
    RecyclerView list = findViewById(R.id.list);
    adapter = new Adapter((index, row) -> {
      Intent intent = new Intent(this, AlumniPairActivity.class);
      intent.putExtra(ExtraKeys.PENDING_INDEX, index);
      if (row != null) {
        intent.putExtra(ExtraKeys.PENDING_ROW, row);
      }
      startActivity(intent);
    });
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (AlumniPending.cached()) {
      showPending(AlumniPending.load(this));
      return;
    }
    AppExecutors.disk().execute(() -> {
      List<AlumniSheet.Row> pending = AlumniPending.load(this);
      AppExecutors.main(() -> showPending(pending));
    });
  }

  private void showPending(List<AlumniSheet.Row> pending) {
    rows.clear();
    if (pending != null) {
      rows.addAll(pending);
    }
    adapter.submit(rows);
    empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    subtitle.setText(
      rows.isEmpty()
        ? getString(R.string.alumni_pair_hint)
        : getString(R.string.alumni_pair_count, rows.size()));
    final List<AlumniSheet.Row> waiting = new ArrayList<>(rows);
    AppExecutors.query().execute(() -> AlumniMatch.prepare(waiting));
  }

  static class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    interface Listener {
      void onOpen(int index, AlumniSheet.Row row);
    }

    private final Listener listener;
    private final List<AlumniSheet.Row> rows = new ArrayList<>();

    Adapter(Listener listener) {
      this.listener = listener;
    }

    void submit(List<AlumniSheet.Row> next) {
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
        .inflate(R.layout.item_alumni_pending, parent, false);
      return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      holder.bind(rows.get(position), position, listener);
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
      private final TextView name;
      private final TextView meta;

      Holder(View itemView) {
        super(itemView);
        name = itemView.findViewById(R.id.name);
        meta = itemView.findViewById(R.id.meta);
      }

      void bind(AlumniSheet.Row row, int index, Listener listener) {
        name.setText(row.displayName());
        String bday = row.birthMonth > 0
          ? DateUtils.monthDayLabel(row.birthMonth, row.birthDay)
          : itemView.getContext().getString(R.string.alumni_no_sheet_birthday);
        String phone = AlumniDesk.nigeriaDigits(row.phone).length() == 13
          ? AlumniDesk.displayPhone(row.phone)
          : itemView.getContext().getString(R.string.alumni_no_number_short);
        meta.setText(itemView.getContext().getString(R.string.alumni_sheet_line, bday, phone));
        itemView.setOnClickListener(v -> listener.onOpen(index, row));
      }
    }
  }
}
