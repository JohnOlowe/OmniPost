package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.databinding.ItemDayHeaderBinding;
import damjay.publicity.omnipost.databinding.ItemVariableBinding;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import java.util.ArrayList;
import java.util.List;

public class VarAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private static final int TYPE_HEADER = 0;
  private static final int TYPE_ROW = 1;

  public interface Listener {
    void onOpen(CaptionVar var);

    void onBuiltin(CaptionVars.Builtin builtin);
  }

  static final class Row {
    final int kind;
    final String header;
    final String subtitle;
    final CaptionVar user;
    final CaptionVars.Builtin builtin;

    Row(String header, String subtitle) {
      this.kind = TYPE_HEADER;
      this.header = header;
      this.subtitle = subtitle;
      this.user = null;
      this.builtin = null;
    }

    Row(CaptionVar user) {
      this.kind = TYPE_ROW;
      this.header = null;
      this.subtitle = null;
      this.user = user;
      this.builtin = null;
    }

    Row(CaptionVars.Builtin builtin) {
      this.kind = TYPE_ROW;
      this.header = null;
      this.subtitle = null;
      this.user = null;
      this.builtin = builtin;
    }
  }

  private final Listener listener;
  private final List<Row> rows = new ArrayList<>();

  public VarAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<CaptionVar> yours) {
    rows.clear();
    rows.add(new Row("Inbuilt", "Always there. Tap one to see what it fills."));
    for (CaptionVars.Builtin builtin : CaptionVars.builtins()) {
      rows.add(new Row(builtin));
    }
    rows.add(new Row("Yours",
      yours == null || yours.isEmpty()
        ? "Tap + to add {theme}, {venue}, or a range that uses {today} and {date}."
        : "These fill in every caption. Values may use other tokens."));
    if (yours != null) {
      for (CaptionVar item : yours) {
        rows.add(new Row(item));
      }
    }
    notifyDataSetChanged();
  }

  @Override
  public int getItemViewType(int position) {
    return rows.get(position).kind;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == TYPE_HEADER) {
      return new HeaderHolder(ItemDayHeaderBinding.inflate(inflater, parent, false));
    }
    return new VarHolder(ItemVariableBinding.inflate(inflater, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    Row row = rows.get(position);
    if (holder instanceof HeaderHolder) {
      ((HeaderHolder) holder).bind(row.header, row.subtitle);
    } else if (holder instanceof VarHolder) {
      ((VarHolder) holder).bind(row, listener);
    }
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }

  static class HeaderHolder extends RecyclerView.ViewHolder {
    private final ItemDayHeaderBinding binding;

    HeaderHolder(ItemDayHeaderBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(String header, String subtitle) {
      binding.header.setText(header);
      if (subtitle == null || subtitle.isEmpty()) {
        binding.subtitle.setVisibility(View.GONE);
      } else {
        binding.subtitle.setVisibility(View.VISIBLE);
        binding.subtitle.setText(subtitle);
      }
    }
  }

  static class VarHolder extends RecyclerView.ViewHolder {
    private final ItemVariableBinding binding;

    VarHolder(ItemVariableBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(Row row, Listener listener) {
      if (row.builtin != null) {
        binding.token.setText(CaptionVars.token(row.builtin.name));
        binding.label.setVisibility(View.VISIBLE);
        binding.label.setText(row.builtin.label);
        binding.preview.setVisibility(View.VISIBLE);
        binding.preview.setText(row.builtin.hint);
        binding.getRoot().setOnClickListener(v -> listener.onBuiltin(row.builtin));
        return;
      }
      CaptionVar var = row.user;
      if (var == null) {
        return;
      }
      binding.token.setText(CaptionVars.token(var.name));
      if (var.label == null || var.label.trim().isEmpty()) {
        binding.label.setVisibility(View.GONE);
      } else {
        binding.label.setVisibility(View.VISIBLE);
        binding.label.setText(var.label.trim());
      }
      String value = var.value == null ? "" : var.value.trim();
      if (value.isEmpty()) {
        binding.preview.setVisibility(View.GONE);
      } else {
        binding.preview.setVisibility(View.VISIBLE);
        binding.preview.setText(value);
      }
      binding.getRoot().setOnClickListener(v -> listener.onOpen(var));
    }
  }
}
