package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.ItemDayHeaderBinding;
import damjay.publicity.omnipost.databinding.ItemMemberBinding;
import damjay.publicity.omnipost.scheduler.BirthdayHorizon;
import damjay.publicity.omnipost.scheduler.DateUtils;
import java.util.ArrayList;
import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private static final int TYPE_HEADER = 0;
  private static final int TYPE_MEMBER = 1;

  public interface Listener {
    void onEdit(Member member);

    void onDelete(Member member);
  }

  static final class Row {
    final int kind;
    final String header;
    final String subtitle;
    final Member member;

    Row(String header, String subtitle) {
      this.kind = TYPE_HEADER;
      this.header = header;
      this.subtitle = subtitle;
      this.member = null;
    }

    Row(Member member) {
      this.kind = TYPE_MEMBER;
      this.header = null;
      this.subtitle = null;
      this.member = member;
    }
  }

  private final Listener listener;
  private final List<Row> rows = new ArrayList<>();

  public MemberAdapter(Listener listener) {
    this.listener = listener;
    setHasStableIds(true);
  }

  public void submit(List<BirthdayHorizon.Section> sections) {
    rows.clear();
    if (sections != null) {
      for (BirthdayHorizon.Section section : sections) {
        rows.add(new Row(section.title, section.subtitle));
        for (Member member : section.members) {
          rows.add(new Row(member));
        }
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
    return new Holder(ItemMemberBinding.inflate(inflater, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    Row row = rows.get(position);
    if (holder instanceof HeaderHolder) {
      ((HeaderHolder) holder).bind(row.header, row.subtitle);
    } else if (holder instanceof Holder) {
      ((Holder) holder).bind(row.member, listener);
    }
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }

  @Override
  public long getItemId(int position) {
    Row row = rows.get(position);
    if (row.kind == TYPE_HEADER) {
      String header = row.header == null ? "" : row.header;
      return (long) TYPE_HEADER << 32 | (header.hashCode() & 0xffffffffL);
    }
    long id = row.member == null ? 0L : row.member.id;
    return (long) TYPE_MEMBER << 32 | (id & 0xffffffffL);
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
