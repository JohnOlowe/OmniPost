package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.AlumniSend;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.FragmentAlumniMonthBinding;
import damjay.publicity.omnipost.scheduler.AlumniAddress;
import damjay.publicity.omnipost.scheduler.AlumniCopy;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.AlumniMatch;
import damjay.publicity.omnipost.scheduler.AlumniMonth;
import damjay.publicity.omnipost.scheduler.AlumniTemplates;
import damjay.publicity.omnipost.scheduler.BirthdayHorizon;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskSearch;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.share.WhatsAppRouter;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
  private final List<Task> nags = new ArrayList<>();
  private TaskAdapter.Listener deskListener;
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

      @Override
      public void onEditCaption(Member member, String kind) {
        if (member == null) {
          return;
        }
        Intent intent = new Intent(requireContext(), AlumniCaptionEditActivity.class);
        intent.putExtra(ExtraKeys.MEMBER_ID, member.id);
        intent.putExtra(
          ExtraKeys.CAPTION_KEY,
          AlumniSend.DETAILS.equals(kind) ? AlumniTemplates.DETAILS : AlumniTemplates.HNM);
        startActivity(intent);
      }

      @Override
      public void onNotOnWhatsApp(Member member) {
        parkNotOnWhatsApp(member);
      }
    });
    deskListener = new TaskAdapter.Listener() {
      @Override
      public void onOpen(Task task) {
        Intent intent = new Intent(requireContext(), DraftActivity.class);
        intent.putExtra(ExtraKeys.TASK_ID, task.id);
        startActivity(intent);
      }

      @Override
      public void onMarkPosted(Task task) {
        Toast.makeText(requireContext(), R.string.posted_toast, Toast.LENGTH_SHORT).show();
        Context app = requireContext().getApplicationContext();
        AppExecutors.disk().execute(() -> ScheduleCoordinator.markPosted(app, task.id));
      }

      @Override
      public void onSnooze(Task task) {
        SnoozeChooser.show(requireContext(), task.postAtMillis, until -> {
          Toast.makeText(
            requireContext(),
            getString(R.string.snoozed_until, DateUtils.formatStamp(until)),
            Toast.LENGTH_SHORT)
            .show();
          Context app = requireContext().getApplicationContext();
          AppExecutors.disk().execute(() -> ScheduleCoordinator.snooze(app, task.id, until));
        });
      }

      @Override
      public void onShift(Task task) {
        SnoozeChooser.pickDateTime(requireContext(), when -> {
          Context app = requireContext().getApplicationContext();
          AppExecutors.disk().execute(() -> {
            ScheduleCoordinator.shift(app, task.id, when);
            AppExecutors.main(() -> {
              if (!isAdded()) {
                return;
              }
              Toast.makeText(
                requireContext(),
                getString(R.string.shifted_to, DateUtils.formatStamp(when)),
                Toast.LENGTH_LONG)
                .show();
            });
          });
        });
      }

      @Override
      public void onReopen(Task task) {
        Toast.makeText(requireContext(), R.string.brought_back, Toast.LENGTH_SHORT).show();
        Context app = requireContext().getApplicationContext();
        AppExecutors.disk().execute(() -> ScheduleCoordinator.reopen(app, task.id));
      }

      @Override
      public void onDelete(Task task) {
        new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.delete_task_title)
          .setMessage(getString(R.string.delete_task_body, task.title))
          .setPositiveButton(R.string.delete, (d, w) -> {
            Context app = requireContext().getApplicationContext();
            AppExecutors.disk().execute(() -> ScheduleCoordinator.deleteCustom(app, task.id));
          })
          .setNegativeButton(android.R.string.cancel, null)
          .show();
      }

      @Override
      public void onEditSeries(Task task) {}

      @Override
      public void onForward(Task task) {
        Intent intent = new Intent(requireContext(), AlumniDmActivity.class);
        intent.putExtra(ExtraKeys.TASK_ID, task.id);
        startActivity(intent);
      }

      @Override
      public void onOptions(Task task) {
        showDeskOptions(task);
      }

      @Override
      public void onCaptionReady(Task task) {
        Context app = requireContext().getApplicationContext();
        AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.markCaptionSaved(app, task.id);
          AppExecutors.main(() -> {
            if (!isAdded()) {
              return;
            }
            Toast.makeText(requireContext(), R.string.caption_ready_toast, Toast.LENGTH_SHORT)
              .show();
          });
        });
      }
    };
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
      AlumniMatch.rememberRoster(members);
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
    db.taskDao().observeActive().observe(getViewLifecycleOwner(), list -> {
      nags.clear();
      if (list != null) {
        for (Task task : list) {
          if (isMonthNag(task)) {
            nags.add(task);
          }
        }
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

  private void parkNotOnWhatsApp(Member member) {
    if (member == null) {
      return;
    }
    member.notOnWhatsApp = true;
    drop(birthday, member.id);
    drop(wave, member.id);
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      AppDatabase.get(app).memberDao().update(member);
      ScheduleCoordinator.cancelMemberTasks(app, member.id);
      ScheduleCoordinator.bootstrap(app);
    });
    paint();
    Toast.makeText(requireContext(), R.string.alumni_moved_no_whatsapp, Toast.LENGTH_SHORT).show();
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
    List<AlumniAddress> addresses = Prefs.alumniAddresses(requireContext());
    int left = AlumniMonth.peopleLeft(birthday, wave, sends);
    binding.leftover.setText(
      left == 0
        ? getString(R.string.alumni_people_done)
        : getString(R.string.alumni_people_left, left));
    List<Member> birthdaySource = waveSent
      ? AlumniMonth.sentBirthday(birthday, sends)
      : AlumniMonth.pendingBirthday(birthday, sends);
    List<Member> birthdayView = filterNames(birthdaySource);
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
    List<Task> desk = deskCards();
    adapter.submit(
      birthdayView,
      waveView,
      sentKeys,
      bag,
      addresses,
      month,
      getString(R.string.alumni_section_birthday),
      getString(R.string.alumni_section_birthday_sub),
      getString(R.string.alumni_section_wave),
      getString(R.string.alumni_section_wave_sub),
      desk,
      getString(R.string.alumni_section_nags),
      getString(R.string.alumni_section_nags_sub),
      deskListener);
    boolean empty = birthdayView.isEmpty() && waveView.isEmpty() && desk.isEmpty();
    binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
    if (empty) {
      if (!query.trim().isEmpty()) {
        binding.empty.setText(R.string.search_empty);
      } else if (waveSent) {
        binding.empty.setText(R.string.alumni_sent_empty);
      } else {
        binding.empty.setText(R.string.alumni_people_done);
      }
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
      if (member == null
          || seen.contains(member.id)
          || !Member.isAlumni(member)
          || !AlumniDesk.canWhatsApp(member)) {
        continue;
      }
      if (BirthdayHorizon.nameMatches(member, query)) {
        out.add(member);
      }
    }
    return out;
  }

  private static void drop(List<Member> list, long id) {
    for (int i = list.size() - 1; i >= 0; i--) {
      Member row = list.get(i);
      if (row != null && row.id == id) {
        list.remove(i);
      }
    }
  }

  private static boolean isMonthNag(Task task) {
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return false;
    }
    return TaskTypes.ALUMNI_PHOTO.equals(task.type)
        || TaskTypes.ALUMNI_BIRTHDAY.equals(task.type);
  }

  private List<Task> deskCards() {
    long now = System.currentTimeMillis();
    long lead = Prefs.warningLeadMs(requireContext());
    List<Task> out = TaskSearch.matches(nags, query);
    Collections.sort(
      out,
      (a, b) -> Long.compare(
        TaskStatus.nextRingMillis(a, now, lead),
        TaskStatus.nextRingMillis(b, now, lead)));
    return out;
  }

  private void showDeskOptions(Task task) {
    if (task == null) {
      return;
    }
    ArrayList<String> labels = new ArrayList<>();
    boolean posted = TaskStatus.POSTED.equals(task.status);
    boolean pending = !posted && TaskStatus.captionWorkPending(task);
    if (pending) {
      labels.add(getString(R.string.caption_ready_action));
    }
    if (!posted) {
      labels.add(getString(task.skipCaption ? R.string.turn_captions_on : R.string.turn_captions_off));
    }
    boolean photo = !posted
      && task.memberId > 0L
      && (TaskTypes.ALUMNI_PHOTO.equals(task.type) || TaskTypes.ALUMNI_BIRTHDAY.equals(task.type));
    if (photo) {
      labels.add(getString(R.string.alumni_got_picture));
      labels.add(getString(R.string.alumni_no_picture));
    }
    if (labels.isEmpty()) {
      return;
    }
    CharSequence[] items = labels.toArray(new CharSequence[0]);
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(task.title)
      .setItems(items, (d, which) -> {
        int index = 0;
        if (pending) {
          if (which == index) {
            deskListener.onCaptionReady(task);
            return;
          }
          index++;
        }
        if (!posted) {
          if (which == index) {
            boolean skip = !task.skipCaption;
            Context app = requireContext().getApplicationContext();
            AppExecutors.disk().execute(() -> ScheduleCoordinator.setSkipCaption(app, task.id, skip));
            Toast.makeText(
              requireContext(),
              skip ? R.string.captions_off_toast : R.string.captions_on_toast,
              Toast.LENGTH_SHORT)
              .show();
            return;
          }
          index++;
        }
        if (photo) {
          if (which == index) {
            setAlumniPhoto(task, AlumniDesk.PHOTO_GOT);
            return;
          }
          index++;
          if (which == index) {
            setAlumniPhoto(task, AlumniDesk.PHOTO_NONE);
          }
        }
      })
      .show();
  }

  private void setAlumniPhoto(Task task, String status) {
    if (task == null || task.memberId <= 0L) {
      return;
    }
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.setAlumniPhoto(app, task.memberId, status);
      AppExecutors.main(() -> {
        if (!isAdded()) {
          return;
        }
        Toast.makeText(
          requireContext(),
          AlumniDesk.PHOTO_NONE.equals(status)
            ? R.string.alumni_photo_none_saved
            : R.string.alumni_photo_saved,
          Toast.LENGTH_LONG)
          .show();
      });
    });
  }

  private List<Member> filterNames(List<Member> source) {
    List<Member> out = new ArrayList<>();
    if (source == null) {
      return out;
    }
    for (Member member : source) {
      if (AlumniDesk.canWhatsApp(member) && BirthdayHorizon.nameMatches(member, query)) {
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
