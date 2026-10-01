package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.AlumniSend;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.ItemAlumniDmBinding;
import damjay.publicity.omnipost.databinding.ItemDayHeaderBinding;
import damjay.publicity.omnipost.scheduler.AlumniCopy;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlumniMonthAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private static final int TYPE_HEADER = 0;
  private static final int TYPE_PERSON = 1;

  interface Listener {
    void onCopy(Member member, String caption);

    void onWhatsApp(Member member, String caption);

    void onMarkSent(Member member, String kind, boolean sent);
  }

  static final class Row {
    final int type;
    final String title;
    final String subtitle;
    final Member member;
    final boolean birthday;

    Row(String title, String subtitle) {
      this.type = TYPE_HEADER;
      this.title = title;
      this.subtitle = subtitle;
      this.member = null;
      this.birthday = false;
    }

    Row(Member member, boolean birthday) {
      this.type = TYPE_PERSON;
      this.title = null;
      this.subtitle = null;
      this.member = member;
      this.birthday = birthday;
    }
  }

  private final Listener listener;
  private final List<Row> rows = new ArrayList<>();
  private Set<String> sent = new HashSet<>();
  private Map<String, String> bag;
  private int month;

  AlumniMonthAdapter(Listener listener) {
    this.listener = listener;
  }

  static String key(long memberId, String kind) {
    return memberId + ":" + kind;
  }

  void submit(
    List<Member> birthday,
    List<Member> wave,
    Set<String> sentKeys,
    Map<String, String> bag,
    int month,
    String birthdayTitle,
    String birthdaySub,
    String waveTitle,
    String waveSub) {
    this.bag = bag;
    this.month = month;
    this.sent = sentKeys == null ? new HashSet<>() : sentKeys;
    rows.clear();
    if (birthday != null && !birthday.isEmpty()) {
      rows.add(new Row(birthdayTitle, birthdaySub));
      for (Member member : birthday) {
        rows.add(new Row(member, true));
      }
    }
    rows.add(new Row(waveTitle, waveSub));
    if (wave != null) {
      for (Member member : wave) {
        rows.add(new Row(member, false));
      }
    }
    notifyDataSetChanged();
  }

  @Override
  public int getItemViewType(int position) {
    return rows.get(position).type;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == TYPE_HEADER) {
      return new Header(ItemDayHeaderBinding.inflate(inflater, parent, false));
    }
    return new Person(ItemAlumniDmBinding.inflate(inflater, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    Row row = rows.get(position);
    if (holder instanceof Header) {
      ((Header) holder).bind(row);
      return;
    }
    ((Person) holder).bind(row, month, bag, sent, listener);
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }

  static class Header extends RecyclerView.ViewHolder {
    private final ItemDayHeaderBinding binding;

    Header(ItemDayHeaderBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(Row row) {
      binding.header.setText(row.title);
      binding.subtitle.setText(row.subtitle);
    }
  }

  static class Person extends RecyclerView.ViewHolder {
    private final ItemAlumniDmBinding binding;

    Person(ItemAlumniDmBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(
      Row row,
      int month,
      Map<String, String> bag,
      Set<String> sent,
      Listener listener) {
      Member member = row.member;
      Calendar now = Calendar.getInstance();
      String who = AlumniCopy.greetingName(member, bag);
      String phone = AlumniDesk.hasPhone(member)
        ? AlumniDesk.displayPhone(member.phone)
        : itemView.getContext().getString(R.string.alumni_no_number_short);
      String tag = AlumniDesk.hasPhone(member) ? "tag" : "do not tag";
      binding.name.setText(member.name);
      binding.meta.setText(
        (who.isEmpty() ? tag : who + " · " + tag)
          + " · "
          + phone
          + (row.birthday ? " · birthday" : " · wave"));
      boolean showHnm = AlumniCopy.wantsHnm(
        member,
        row.birthday ? AlumniCopy.KIND_BIRTHDAY : AlumniCopy.KIND_WAVE);
      if (showHnm) {
        String hnm = AlumniCopy.hnm(member, month, now, bag);
        boolean done = sent.contains(key(member.id, AlumniSend.HNM));
        binding.blockHnm.setVisibility(View.VISIBLE);
        binding.labelHnm.setText(R.string.alumni_hnm_label);
        binding.preview.setText(hnm);
        binding.btnCopy.setOnClickListener(v -> listener.onCopy(member, hnm));
        binding.btnWhatsapp.setOnClickListener(v -> listener.onWhatsApp(member, hnm));
        paintSent(binding.btnSentHnm, done, v -> listener.onMarkSent(member, AlumniSend.HNM, !done));
      } else {
        binding.blockHnm.setVisibility(View.GONE);
      }
      if (row.birthday) {
        String details = AlumniCopy.details(member, month, now, bag);
        boolean done = sent.contains(key(member.id, AlumniSend.DETAILS));
        binding.blockDetails.setVisibility(View.VISIBLE);
        binding.previewDetails.setText(details);
        binding.btnCopyDetails.setOnClickListener(v -> listener.onCopy(member, details));
        binding.btnWhatsappDetails.setOnClickListener(v -> listener.onWhatsApp(member, details));
        paintSent(
          binding.btnSentDetails,
          done,
          v -> listener.onMarkSent(member, AlumniSend.DETAILS, !done));
      } else {
        binding.blockDetails.setVisibility(View.GONE);
      }
    }

    private static void paintSent(
      com.google.android.material.button.MaterialButton button,
      boolean done,
      View.OnClickListener listener) {
      button.setVisibility(View.VISIBLE);
      button.setText(done ? R.string.alumni_sent_undo : R.string.alumni_ive_sent);
      button.setOnClickListener(listener);
    }
  }
}
