package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.databinding.ItemVariableBinding;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import java.util.ArrayList;
import java.util.List;

public class VarAdapter extends RecyclerView.Adapter<VarAdapter.Holder> {
  public interface Listener {
    void onOpen(CaptionVar var);
  }

  private final Listener listener;
  private final List<CaptionVar> items = new ArrayList<>();

  public VarAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<CaptionVar> vars) {
    items.clear();
    if (vars != null) {
      items.addAll(vars);
    }
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(
      ItemVariableBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder holder, int position) {
    holder.bind(items.get(position), listener);
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static class Holder extends RecyclerView.ViewHolder {
    private final ItemVariableBinding binding;

    Holder(ItemVariableBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(CaptionVar var, Listener listener) {
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
