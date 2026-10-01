package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.ItemAlumniDmBinding;
import damjay.publicity.omnipost.scheduler.AlumniCopy;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import java.util.ArrayList;
import java.util.List;

public class AlumniDmAdapter extends RecyclerView.Adapter<AlumniDmAdapter.Holder> {
  interface Listener {
    void onSend(Member member, String caption, boolean openChat);
  }

  private final Listener listener;
  private final List<Member> rows = new ArrayList<>();
  private String mode = AlumniCopy.KIND_WAVE;
  private int month;

  AlumniDmAdapter(Listener listener) {
    this.listener = listener;
  }

  void submit(List<Member> members, String mode, int month) {
    this.mode = mode == null ? AlumniCopy.KIND_WAVE : mode;
    this.month = month;
    rows.clear();
    if (members != null) {
      rows.addAll(members);
    }
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(ItemAlumniDmBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder holder, int position) {
    holder.bind(rows.get(position), mode, month, listener);
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }

  static class Holder extends RecyclerView.ViewHolder {
    private final ItemAlumniDmBinding binding;

    Holder(ItemAlumniDmBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(Member member, String mode, int month, Listener listener) {
      binding.name.setText(member.name);
      String caption;
      if (AlumniCopy.KIND_PHOTO.equals(mode)) {
        caption = AlumniCopy.photo(member);
      } else if (AlumniCopy.KIND_BIRTHDAY.equals(mode) || member.birthMonth == month) {
        caption = AlumniCopy.dm(member, month);
      } else {
        caption = AlumniCopy.dm(member, month);
      }
      String who = AlumniCopy.greetingName(member);
      String phone = AlumniDesk.hasPhone(member) ? AlumniDesk.displayPhone(member.phone) : "no number";
      String tag = AlumniDesk.hasPhone(member) ? "tag" : "do not tag";
      binding.meta.setText(
        (who.isEmpty() ? tag : who + " · " + tag)
          + " · "
          + phone
          + (member.introduced ? " · intro sent" : " · first DM"));
      binding.preview.setText(caption);
      binding.btnCopy.setOnClickListener(v -> listener.onSend(member, caption, false));
      binding.btnWhatsapp.setOnClickListener(v -> listener.onSend(member, caption, true));
    }
  }
}
