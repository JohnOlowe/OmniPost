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
import damjay.publicity.omnipost.scheduler.AlumniTemplates;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.List;

public class AlumniCaptionsActivity extends AppCompatActivity {
  private Adapter adapter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_captions);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    adapter = new Adapter(key -> {
      Intent intent = new Intent(this, AlumniCaptionEditActivity.class);
      intent.putExtra(ExtraKeys.CAPTION_KEY, key);
      startActivity(intent);
    });
    RecyclerView list = findViewById(R.id.list);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);
  }

  @Override
  protected void onResume() {
    super.onResume();
    List<Row> rows = new ArrayList<>();
    for (String key : AlumniTemplates.KEYS) {
      Row row = new Row();
      row.key = key;
      row.title = AlumniTemplates.label(key);
      String value = Prefs.alumniCaption(this, key);
      row.preview = value == null ? "" : value.replace('\n', ' ').trim();
      rows.add(row);
    }
    adapter.submit(rows);
  }

  static final class Row {
    String key;
    String title;
    String preview;
  }

  static class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    interface Listener {
      void onOpen(String key);
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();

    Adapter(Listener listener) {
      this.listener = listener;
    }

    void submit(List<Row> next) {
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
        .inflate(R.layout.item_variable, parent, false);
      return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      holder.bind(rows.get(position), listener);
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
      private final TextView token;
      private final TextView label;
      private final TextView preview;

      Holder(View itemView) {
        super(itemView);
        token = itemView.findViewById(R.id.token);
        label = itemView.findViewById(R.id.label);
        preview = itemView.findViewById(R.id.preview);
      }

      void bind(Row row, Listener listener) {
        token.setText(row.title);
        label.setText(AlumniTemplates.hint(row.key));
        preview.setText(row.preview);
        itemView.setOnClickListener(v -> listener.onOpen(row.key));
      }
    }
  }
}
