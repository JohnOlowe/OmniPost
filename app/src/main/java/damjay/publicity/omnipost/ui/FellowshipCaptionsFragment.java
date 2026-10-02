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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.databinding.FragmentFellowshipCaptionsBinding;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.scheduler.FellowshipTemplates;
import damjay.publicity.omnipost.share.WhatsAppPreview;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.List;

public class FellowshipCaptionsFragment extends Fragment {
  private FragmentFellowshipCaptionsBinding binding;
  private Adapter adapter;
  private List<CaptionVar> vars = new ArrayList<>();
  private List<Series> series = new ArrayList<>();

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentFellowshipCaptionsBinding.inflate(inflater, container, false);
    adapter = new Adapter(this::open);
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    binding.fab.setOnClickListener(v -> openVar(0L));
    AppDatabase db = AppDatabase.get(requireContext());
    db.captionVarDao().observeAll().observe(getViewLifecycleOwner(), list -> {
      vars.clear();
      if (list != null) {
        vars.addAll(list);
      }
      paint();
    });
    db.seriesDao().observeAll().observe(getViewLifecycleOwner(), list -> {
      series.clear();
      if (list != null) {
        series.addAll(list);
      }
      paint();
    });
    return binding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    paint();
  }

  private void paint() {
    if (adapter == null || getContext() == null) {
      return;
    }
    adapter.submit(catalog(requireContext(), vars, series));
  }

  private void open(Row row) {
    if (row == null || row.header) {
      return;
    }
    if (row.builtin != null) {
      new MaterialAlertDialogBuilder(requireContext())
        .setTitle(CaptionVars.token(row.builtin.name))
        .setMessage(row.builtin.label + "\n\n" + row.builtin.hint)
        .setPositiveButton(android.R.string.ok, null)
        .show();
      return;
    }
    if (row.varId > 0L) {
      openVar(row.varId);
      return;
    }
    Intent intent = new Intent(requireContext(), FellowshipCaptionEditActivity.class);
    if (row.seriesId > 0L) {
      intent.putExtra(ExtraKeys.SERIES_ID, row.seriesId);
    } else {
      intent.putExtra(ExtraKeys.CAPTION_KEY, row.key);
    }
    startActivity(intent);
  }

  private void openVar(long id) {
    Intent intent = new Intent(requireContext(), VariableEditActivity.class);
    if (id > 0L) {
      intent.putExtra(ExtraKeys.VAR_ID, id);
    }
    startActivity(intent);
  }

  static List<Row> catalog(Context ctx, List<CaptionVar> vars, List<Series> series) {
    List<Row> rows = new ArrayList<>();
    rows.add(header(
      ctx.getString(R.string.tokens_inbuilt),
      ctx.getString(R.string.tokens_inbuilt_sub)));
    for (CaptionVars.Builtin builtin : CaptionVars.builtins()) {
      Row row = new Row();
      row.builtin = builtin;
      row.title = CaptionVars.token(builtin.name);
      row.preview = builtin.hint;
      row.label = builtin.label;
      rows.add(row);
    }
    rows.add(header(
      ctx.getString(R.string.tokens_yours),
      vars == null || vars.isEmpty()
        ? ctx.getString(R.string.tokens_yours_empty)
        : ctx.getString(R.string.tokens_yours_sub)));
    if (vars != null) {
      for (CaptionVar item : vars) {
        if (item == null) {
          continue;
        }
        Row row = new Row();
        row.varId = item.id;
        row.title = CaptionVars.token(item.name);
        row.label = item.label;
        row.preview = item.value == null ? "" : item.value;
        rows.add(row);
      }
    }
    rows.add(header(
      ctx.getString(R.string.fellowship_series_captions),
      ctx.getString(R.string.fellowship_series_captions_sub)));
    if (series != null) {
      for (Series item : series) {
        if (item == null) {
          continue;
        }
        Row row = new Row();
        row.seriesId = item.id;
        row.title = item.title;
        row.key = item.kind;
        String caption = item.caption == null ? "" : item.caption.replace('\n', ' ').trim();
        row.preview = caption.isEmpty() ? ctx.getString(R.string.fellowship_empty_caption) : caption;
        row.label = ctx.getString(R.string.series_caption);
        rows.add(row);
      }
    }
    rows.add(header(
      ctx.getString(R.string.fellowship_weekly_captions),
      ctx.getString(R.string.fellowship_weekly_captions_sub)));
    for (String key : FellowshipTemplates.KEYS) {
      Row row = new Row();
      row.key = key;
      row.title = FellowshipTemplates.label(ctx, key);
      String custom = Prefs.fellowshipCaptionRaw(ctx, key);
      row.preview = custom == null || custom.trim().isEmpty()
        ? ctx.getString(R.string.fellowship_empty_caption)
        : custom.replace('\n', ' ').trim();
      row.label = FellowshipTemplates.hint(ctx, key);
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
    long seriesId;
    long varId;
    CaptionVars.Builtin builtin;
    String key;
    String title;
    String label;
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
        if (row.label == null || row.label.trim().isEmpty()) {
          label.setVisibility(View.GONE);
        } else {
          label.setVisibility(View.VISIBLE);
          label.setText(row.label);
        }
        if (row.preview == null || row.preview.trim().isEmpty()) {
          preview.setVisibility(View.GONE);
        } else {
          preview.setVisibility(View.VISIBLE);
          if (row.builtin != null || row.varId > 0L) {
            preview.setText(row.preview);
          } else {
            WhatsAppPreview.show(preview, row.preview);
          }
        }
        itemView.setOnClickListener(v -> listener.onOpen(row));
      }
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
