package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.ItemAlumniDmBinding;
import damjay.publicity.omnipost.scheduler.AlumniAddress;
import damjay.publicity.omnipost.scheduler.AlumniCopy;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.AlumniSheet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlumniDmAdapter extends RecyclerView.Adapter<AlumniDmAdapter.Holder> {
  interface Listener {
    void onSend(Member member, String caption, boolean openChat);
  }

  private final Listener listener;
  private final List<Member> rows = new ArrayList<>();
  private String mode = AlumniCopy.KIND_WAVE;
  private int month;
  private Map<String, String> bag = new LinkedHashMap<>();
  private List<AlumniAddress> addresses = new ArrayList<>();

  AlumniDmAdapter(Listener listener) {
    this.listener = listener;
  }

  void submit(
    List<Member> members,
    String mode,
    int month,
    Map<String, String> bag,
    List<AlumniAddress> addresses) {
    this.mode = mode == null ? AlumniCopy.KIND_WAVE : mode;
    this.month = month;
    this.bag = bag == null ? new LinkedHashMap<>() : bag;
    this.addresses = addresses == null ? new ArrayList<>() : addresses;
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
    holder.bind(rows.get(position), mode, month, bag, addresses, listener);
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

    void bind(
      Member member,
      String mode,
      int month,
      Map<String, String> bag,
      List<AlumniAddress> addresses,
      Listener listener) {
      binding.name.setText(member.name);
      Calendar now = Calendar.getInstance();
      boolean photo = AlumniCopy.KIND_PHOTO.equals(mode);
      boolean showHnm = AlumniCopy.wantsHnm(member, mode);
      boolean showDetails = AlumniCopy.wantsDetails(member, mode, month);
      String who = AlumniCopy.greetingName(member, bag);
      String phone = AlumniDesk.hasPhone(member) ? AlumniDesk.displayPhone(member.phone) : "no number";
      String tag = AlumniDesk.hasPhone(member) ? "tag" : "do not tag";
      String also = AlumniSheet.namesDiffer(member)
        ? " · " + AlumniSheet.sheetName(member)
        : "";
      binding.meta.setText(
        (who.isEmpty() ? tag : who + " · " + tag)
          + " · "
          + phone
          + also
          + (member.introduced ? " · intro sent" : " · first DM"));
      hideSentButtons();
      if (photo) {
        String caption = AlumniCopy.photo(member, now, bag, addresses);
        binding.blockHnm.setVisibility(View.VISIBLE);
        binding.labelHnm.setText(R.string.alumni_desk_photo);
        binding.preview.setText(caption);
        binding.btnCopy.setOnClickListener(v -> listener.onSend(member, caption, false));
        binding.btnWhatsapp.setOnClickListener(v -> listener.onSend(member, caption, true));
        binding.blockDetails.setVisibility(View.GONE);
        return;
      }
      if (showHnm) {
        String hnm = AlumniCopy.hnm(member, month, now, bag, addresses);
        binding.blockHnm.setVisibility(View.VISIBLE);
        binding.labelHnm.setText(R.string.alumni_hnm_label);
        binding.preview.setText(hnm);
        binding.btnCopy.setOnClickListener(v -> listener.onSend(member, hnm, false));
        binding.btnWhatsapp.setOnClickListener(v -> listener.onSend(member, hnm, true));
      } else {
        binding.blockHnm.setVisibility(View.GONE);
      }
      if (showDetails) {
        String details = AlumniCopy.details(member, month, now, bag, addresses);
        binding.blockDetails.setVisibility(View.VISIBLE);
        binding.previewDetails.setText(details);
        binding.btnCopyDetails.setOnClickListener(v -> listener.onSend(member, details, false));
        binding.btnWhatsappDetails.setOnClickListener(v -> listener.onSend(member, details, true));
      } else {
        binding.blockDetails.setVisibility(View.GONE);
      }
    }

    private void hideSentButtons() {
      if (binding.btnSentHnm != null) {
        binding.btnSentHnm.setVisibility(View.GONE);
      }
      if (binding.btnSentDetails != null) {
        binding.btnSentDetails.setVisibility(View.GONE);
      }
      if (binding.btnEditHnm != null) {
        binding.btnEditHnm.setVisibility(View.GONE);
      }
      if (binding.btnEditDetails != null) {
        binding.btnEditDetails.setVisibility(View.GONE);
      }
    }
  }
}
