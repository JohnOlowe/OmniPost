package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.ItemMemberBinding;
import damjay.publicity.omnipost.scheduler.DateUtils;
import java.util.ArrayList;
import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.Holder> {
  public interface Listener {
    void onEdit(Member member);

    void onDelete(Member member);
  }

  private final Listener listener;
  private final List<Member> items = new ArrayList<>();

  public MemberAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<Member> members) {
    items.clear();
    if (members != null) {
      items.addAll(members);
    }
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(ItemMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
    private final ItemMemberBinding binding;

    Holder(ItemMemberBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(Member member, Listener listener) {
      binding.name.setText(member.name);
      binding.when.setText(DateUtils.monthDayLabel(member.birthMonth, member.birthDay));
      binding.getRoot().setOnClickListener(v -> listener.onEdit(member));
      binding.btnDelete.setOnClickListener(v -> listener.onDelete(member));
    }
  }
}
