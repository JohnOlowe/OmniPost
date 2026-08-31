package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.databinding.ItemDraftBinding;
import damjay.publicity.omnipost.scheduler.DateUtils;
import java.util.ArrayList;
import java.util.List;

public class DraftAdapter extends RecyclerView.Adapter<DraftAdapter.Holder> {
  public interface Listener {
    void onOpen(Draft draft);

    void onDelete(Draft draft);
  }

  private final Listener listener;
  private final List<Draft> items = new ArrayList<>();

  public DraftAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<Draft> drafts) {
    items.clear();
    if (drafts != null) {
      items.addAll(drafts);
    }
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(ItemDraftBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
    private final ItemDraftBinding binding;

    Holder(ItemDraftBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(Draft draft, Listener listener) {
      binding.title.setText(draft.title == null || draft.title.isEmpty() ? "Untitled caption" : draft.title);
      String preview = firstNonEmpty(draft.finalizedText, draft.variantA, draft.variantB);
      binding.preview.setText(preview);
      binding.updated.setText(DateUtils.formatStamp(draft.updatedAt));
      binding.getRoot().setOnClickListener(v -> listener.onOpen(draft));
      binding.btnDelete.setOnClickListener(v -> listener.onDelete(draft));
    }

    private static String firstNonEmpty(String... values) {
      if (values == null) {
        return "";
      }
      for (String value : values) {
        if (value != null && !value.trim().isEmpty()) {
          return value.trim();
        }
      }
      return "";
    }
  }
}
