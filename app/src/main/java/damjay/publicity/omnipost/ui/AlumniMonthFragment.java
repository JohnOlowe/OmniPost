package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.AlumniSend;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.FragmentAlumniMonthBinding;
import damjay.publicity.omnipost.scheduler.AlumniCopy;
import damjay.publicity.omnipost.scheduler.AlumniMonth;
import damjay.publicity.omnipost.scheduler.BirthdayHorizon;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.share.WhatsAppRouter;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlumniMonthFragment extends Fragment {
  private FragmentAlumniMonthBinding binding;
  private AlumniMonthAdapter adapter;
  private final List<Member> roster = new ArrayList<>();
  private final List<Member> birthday = new ArrayList<>();
  private final List<Member> wave = new ArrayList<>();
  private final List<AlumniSend> sends = new ArrayList<>();
  private boolean waveSent;
  private int month;
  private int yearMonth;
  private Map<String, String> bag;
  private String query = "";

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentAlumniMonthBinding.inflate(inflater, container, false);
    Calendar now = Calendar.getInstance();
    month = now.get(Calendar.MONTH) + 1;
    yearMonth = AlumniMonth.yearMonth(now);
    bag = Prefs.alumniCaptionBag(requireContext());
    adapter = new AlumniMonthAdapter(new AlumniMonthAdapter.Listener() {
      @Override
      public void onCopy(Member member, String caption) {
        send(member, caption, false);
      }

      @Override
      public void onWhatsApp(Member member, String caption) {
        send(member, caption, true);
      }

      @Override
      public void onMarkSent(Member member, String kind, boolean sent) {
        mark(member, kind, sent);
      }
    });
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    binding.waveFilter.check(R.id.chip_to_send);
    binding.waveFilter.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
      if (!isChecked) {
        return;
      }
      waveSent = checkedId == R.id.chip_sent;
      paint();
    });
    binding.search.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        query = s == null ? "" : s.toString();
        paint();
      }
    });
    binding.btnGroup.setOnClickListener(v -> {
      String group = AlumniCopy.group(month, Calendar.getInstance(), bag);
      WhatsAppRouter.copyToClipboard(requireContext(), group);
      if (!WhatsAppRouter.openApp(requireContext())) {
        Toast.makeText(requireContext(), R.string.whatsapp_missing, Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(requireContext(), R.string.alumni_copied_group, Toast.LENGTH_LONG).show();
      }
    });
    AppDatabase db = AppDatabase.get(requireContext());
    db.memberDao().observeAll().observe(getViewLifecycleOwner(), members -> {
      roster.clear();
      birthday.clear();
      wave.clear();
      if (members != null) {
        roster.addAll(members);
      }
      birthday.addAll(AlumniMonth.birthdayPeople(members, month));
      wave.addAll(AlumniMonth.wavePeople(members, month));
      paint();
    });
    db.alumniSendDao().observeMonth(yearMonth).observe(getViewLifecycleOwner(), list -> {
      sends.clear();
      if (list != null) {
        sends.addAll(list);
      }
      paint();
    });
    return binding.getRoot();
  }

  private void send(Member member, String caption, boolean openChat) {
    WhatsAppRouter.copyToClipboard(requireContext(), caption);
    AppExecutors.disk().execute(() ->
      ScheduleCoordinator.markAlumniIntroduced(requireContext().getApplicationContext(), member.id));
    boolean ok = true;
    if (openChat) {
      ok = WhatsAppRouter.openChat(requireContext(), member.phone, caption);
    }
    Toast.makeText(
      requireContext(),
      openChat
        ? (ok ? R.string.alumni_copied_chat : R.string.whatsapp_missing)
        : R.string.alumni_copied_dm,
      Toast.LENGTH_LONG)
      .show();
  }

  private void mark(Member member, String kind, boolean sent) {
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      if (sent) {
        ScheduleCoordinator.markAlumniSent(app, member.id, kind);
      } else {
        ScheduleCoordinator.unmarkAlumniSent(app, member.id, kind);
      }
      AppExecutors.main(() -> {
        if (!isAdded()) {
          return;
        }
        Toast.makeText(
          requireContext(),
          sent ? R.string.alumni_message_sent : R.string.alumni_message_undone,
          Toast.LENGTH_SHORT)
          .show();
      });
    });
  }

  private void paint() {
    if (binding == null || adapter == null) {
      return;
    }
    bag = Prefs.alumniCaptionBag(requireContext());
    int left = AlumniMonth.peopleLeft(birthday, wave, sends);
    binding.leftover.setText(
      left == 0
        ? getString(R.string.alumni_people_done)
        : getString(R.string.alumni_people_left, left));
    List<Member> birthdayView = filterNames(birthday);
    List<Member> waveSource = waveSent
      ? AlumniMonth.sentWave(wave, sends)
      : AlumniMonth.pendingWave(wave, sends);
    if (!query.trim().isEmpty()) {
      waveSource = restMatches(birthdayView);
    }
    List<Member> waveView = filterNames(waveSource);
    Set<String> sentKeys = new HashSet<>();
    for (AlumniSend send : sends) {
      if (send != null) {
        sentKeys.add(AlumniMonthAdapter.key(send.memberId, send.kind));
      }
    }
    adapter.submit(
      birthdayView,
      waveView,
      sentKeys,
      bag,
      month,
      getString(R.string.alumni_section_birthday),
      getString(R.string.alumni_section_birthday_sub),
      getString(R.string.alumni_section_wave),
      getString(R.string.alumni_section_wave_sub));
    boolean empty = birthdayView.isEmpty() && waveView.isEmpty();
    binding.empty.setVisibility(empty && !query.trim().isEmpty() ? View.VISIBLE : View.GONE);
    if (empty && !query.trim().isEmpty()) {
      binding.empty.setText(R.string.search_empty);
    }
  }

  private List<Member> restMatches(List<Member> birthdayView) {
    java.util.Set<Long> seen = new HashSet<>();
    for (Member member : birthdayView) {
      if (member != null) {
        seen.add(member.id);
      }
    }
    List<Member> out = new ArrayList<>();
    for (Member member : roster) {
      if (member == null || seen.contains(member.id) || !Member.isAlumni(member)) {
        continue;
      }
      if (BirthdayHorizon.nameMatches(member, query)) {
        out.add(member);
      }
    }
    return out;
  }

  private List<Member> filterNames(List<Member> source) {
    List<Member> out = new ArrayList<>();
    if (source == null) {
      return out;
    }
    for (Member member : source) {
      if (BirthdayHorizon.nameMatches(member, query)) {
        out.add(member);
      }
    }
    return out;
  }

  @Override
  public void onResume() {
    super.onResume();
    paint();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
