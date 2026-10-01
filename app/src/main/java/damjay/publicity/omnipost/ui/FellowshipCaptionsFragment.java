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
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.databinding.FragmentFellowshipCaptionsBinding;
import damjay.publicity.omnipost.scheduler.FellowshipTemplates;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.List;

public class FellowshipCaptionsFragment extends Fragment {
  private FragmentFellowshipCaptionsBinding binding;
  private Adapter adapter;

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
    AppDatabase.get(requireContext())
      .seriesDao()
      .observeAll()
      .observe(getViewLifecycleOwner(), series -> adapter.submit(catalog(requireContext(), series)));
    return binding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (adapter == null || getContext() == null) {
      return;
    }
    reload();
  }

  private void reload() {
    damjay.publicity.omnipost.util.AppExecutors.query().execute(() -> {
      List<Series> series = AppDatabase.get(requireContext()).seriesDao().getAllSync();
      damjay.publicity.omnipost.util.AppExecutors.main(() -> {
        if (!isAdded() || adapter == null) {
          return;
        }
        adapter.submit(catalog(requireContext(), series));
      });
    });
  }

  private void open(Row row) {
    if (row == null || row.header) {
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

  static List<Row> catalog(Context ctx, List<Series> series) {
    List<Row> rows = new ArrayList<>();
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
      row.preview = custom == null
        ? ctx.getString(R.string.fellowship_empty_caption)
        : custom.replace('\n', ' ').trim();
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
        if (row.seriesId > 0L) {
          label.setText(itemView.getContext().getString(R.string.series_caption));
        } else {
          label.setText(FellowshipTemplates.hint(itemView.getContext(), row.key));
        }
        preview.setText(row.preview);
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
