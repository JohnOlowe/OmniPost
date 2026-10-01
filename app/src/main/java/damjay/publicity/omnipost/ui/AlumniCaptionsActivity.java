package damjay.publicity.omnipost.ui;

import android.content.Context;
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
import damjay.publicity.omnipost.scheduler.AlumniAddress;
import damjay.publicity.omnipost.scheduler.AlumniTemplates;
import damjay.publicity.omnipost.scheduler.CaptionVars;
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
    findViewById(R.id.btn_new_address).setOnClickListener(v ->
      startActivity(new Intent(this, AlumniAddressEditActivity.class)));
    adapter = new Adapter(this::open);
    RecyclerView list = findViewById(R.id.list);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);
  }

  @Override
  protected void onResume() {
    super.onResume();
    adapter.submit(catalog(this));
  }

  private void open(Row row) {
    if (row == null || row.header) {
      return;
    }
    if (row.address) {
      Intent intent = new Intent(this, AlumniAddressEditActivity.class);
      intent.putExtra(ExtraKeys.ADDRESS_NAME, row.key);
      startActivity(intent);
      return;
    }
    Intent intent = new Intent(this, AlumniCaptionEditActivity.class);
    intent.putExtra(ExtraKeys.CAPTION_KEY, row.key);
    startActivity(intent);
  }

  static List<Row> catalog(Context ctx) {
    List<Row> rows = new ArrayList<>();
    rows.add(header(
      ctx.getString(R.string.alumni_address_section),
      ctx.getString(R.string.alumni_address_section_sub)));
    List<AlumniAddress> addresses = Prefs.alumniAddresses(ctx);
    for (AlumniAddress item : addresses) {
      if (item == null || item.name == null || item.name.isEmpty()) {
        continue;
      }
      Row row = new Row();
      row.address = true;
      row.key = item.name;
      row.title = CaptionVars.token(item.name);
      String label = item.label == null || item.label.isEmpty() ? item.name : item.label;
      String male = item.male == null ? "" : item.male.replace('\n', ' ').trim();
      String female = item.female == null ? "" : item.female.replace('\n', ' ').trim();
      row.preview = label + " · " + male + " / " + female;
      rows.add(row);
    }
    rows.add(header(
      ctx.getString(R.string.alumni_messages_section),
      ctx.getString(R.string.alumni_messages_section_sub)));
    for (String key : AlumniTemplates.KEYS) {
      Row row = new Row();
      row.key = key;
      row.title = AlumniTemplates.label(key);
      String value = Prefs.alumniCaption(ctx, key);
      row.preview = value == null ? "" : value.replace('\n', ' ').trim();
      rows.add(row);
    }
    return rows;
  }

  private static Row header(String title, String subtitle) {
    Row row = new Row();
    row.header = true;
    row.title = title;
    row.preview = subtitle;
    return row;
  }

  static final class Row {
    boolean header;
    boolean address;
    String key;
    String title;
    String preview;
  }

  static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface Listener {
      void onOpen(Row row);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

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

    @Override
    public int getItemViewType(int position) {
      return rows.get(position).header ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      if (viewType == TYPE_HEADER) {
        return new Header(inflater.inflate(R.layout.item_day_header, parent, false));
      }
      return new Holder(inflater.inflate(R.layout.item_variable, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
      Row row = rows.get(position);
      if (holder instanceof Header) {
        ((Header) holder).bind(row);
        return;
      }
      ((Holder) holder).bind(row, listener);
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    static class Header extends RecyclerView.ViewHolder {
      private final TextView title;
      private final TextView subtitle;

      Header(View itemView) {
        super(itemView);
        title = itemView.findViewById(R.id.header);
        subtitle = itemView.findViewById(R.id.subtitle);
      }

      void bind(Row row) {
        title.setText(row.title);
        subtitle.setText(row.preview);
      }
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
        label.setText(row.address ? CaptionVars.token(row.key) : AlumniTemplates.hint(row.key));
        preview.setText(row.preview);
        itemView.setOnClickListener(v -> listener.onOpen(row));
      }
    }
  }
}
