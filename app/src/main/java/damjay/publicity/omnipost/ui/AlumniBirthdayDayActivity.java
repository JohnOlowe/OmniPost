package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ActivityAlumniBirthdayDayBinding;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.share.WhatsAppRouter;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One day's alumni birthday flyers, opened from the packed post nag. */
public class AlumniBirthdayDayActivity extends AppCompatActivity {
  private ActivityAlumniBirthdayDayBinding binding;
  private TaskAdapter adapter;
  private final List<Task> cards = new ArrayList<>();
  private final Map<Long, Member> people = new HashMap<>();
  private String dayKey = "";
  private long packId;
  private Map<String, String> bag;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityAlumniBirthdayDayBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    packId = getIntent().getLongExtra(ExtraKeys.TASK_ID, 0L);
    dayKey = getIntent().getStringExtra(ExtraKeys.DAY_KEY);
    if (dayKey == null) {
      dayKey = "";
    }
    bag = Prefs.alumniCaptionBag(this);
    adapter = new TaskAdapter(new TaskAdapter.Listener() {
      @Override
      public void onOpen(Task task) {
        Intent intent = new Intent(AlumniBirthdayDayActivity.this, DraftActivity.class);
        intent.putExtra(ExtraKeys.TASK_ID, task.id);
        startActivity(intent);
      }

      @Override
      public void onMarkPosted(Task task) {
        Toast.makeText(AlumniBirthdayDayActivity.this, R.string.posted_toast, Toast.LENGTH_SHORT)
          .show();
        Context app = getApplicationContext();
        AppExecutors.disk().execute(() -> ScheduleCoordinator.markPosted(app, task.id));
      }

      @Override
      public void onSnooze(Task task) {
        SnoozeChooser.show(AlumniBirthdayDayActivity.this, task.postAtMillis, until -> {
          Toast.makeText(
            AlumniBirthdayDayActivity.this,
            getString(R.string.snoozed_until, DateUtils.formatStamp(until)),
            Toast.LENGTH_SHORT)
            .show();
          Context app = getApplicationContext();
          AppExecutors.disk().execute(() -> ScheduleCoordinator.snooze(app, task.id, until));
        });
      }

      @Override
      public void onShift(Task task) {
        SnoozeChooser.pickDateTime(AlumniBirthdayDayActivity.this, when -> {
          Context app = getApplicationContext();
          AppExecutors.disk().execute(() -> {
            ScheduleCoordinator.shift(app, task.id, when);
            AppExecutors.main(() -> Toast.makeText(
              AlumniBirthdayDayActivity.this,
              getString(R.string.shifted_to, DateUtils.formatStamp(when)),
              Toast.LENGTH_LONG)
              .show());
          });
        });
      }

      @Override
      public void onReopen(Task task) {
        Toast.makeText(AlumniBirthdayDayActivity.this, R.string.brought_back, Toast.LENGTH_SHORT)
          .show();
        Context app = getApplicationContext();
        AppExecutors.disk().execute(() -> ScheduleCoordinator.reopen(app, task.id));
      }

      @Override
      public void onDelete(Task task) {}

      @Override
      public void onEditSeries(Task task) {}

      @Override
      public void onForward(Task task) {
        copyFlyer(memberOf(task));
      }

      @Override
      public void onOptions(Task task) {
        showOptions(task);
      }

      @Override
      public void onCaptionReady(Task task) {
        Context app = getApplicationContext();
        AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.markCaptionSaved(app, task.id);
          AppExecutors.main(() -> Toast.makeText(
            AlumniBirthdayDayActivity.this, R.string.caption_ready_toast, Toast.LENGTH_SHORT)
            .show());
        });
      }

      @Override
      public void onOpenPack(Task task) {
        onOpen(task);
      }
    });
    binding.list.setLayoutManager(new LinearLayoutManager(this));
    binding.list.setAdapter(adapter);
    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnCopy.setOnClickListener(v -> copyAll());
    AppDatabase.get(this).taskDao().observeActive().observe(this, unused -> load());
    AppDatabase.get(this).taskDao().observePosted().observe(this, unused -> load());
  }

  @Override
  protected void onResume() {
    super.onResume();
    load();
  }

  private void load() {
    final long pack = packId;
    final String given = dayKey;
    AppExecutors.disk().execute(() -> {
      AppDatabase db = AppDatabase.get(this);
      String day = given;
      Task packTask = pack > 0L ? db.taskDao().getById(pack) : null;
      if ((day == null || day.isEmpty()) && packTask != null) {
        day = TaskTypes.dayKey(packTask);
      }
      List<Task> found = TaskTypes.alumniBirthdaysOnDay(db.taskDao().getAllSync(), day);
      List<Member> roster = db.memberDao().getAllSync();
      Map<String, String> captions = Prefs.alumniCaptionBag(this);
      String title = packTask != null && packTask.title != null && !packTask.title.isEmpty()
        ? packTask.title
        : getString(R.string.alumni_open_birthdays);
      String resolved = day == null ? "" : day;
      AppExecutors.main(() -> {
        if (binding == null) {
          return;
        }
        dayKey = resolved;
        bag = captions;
        people.clear();
        if (roster != null) {
          for (Member member : roster) {
            if (member != null && member.id > 0L) {
              people.put(member.id, member);
            }
          }
        }
        cards.clear();
        if (found != null) {
          cards.addAll(found);
        }
        binding.title.setText(title);
        adapter.submit(
          cards, false, System.currentTimeMillis(), Prefs.warningLeadMs(this));
        binding.empty.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
      });
    });
  }

  private Member memberOf(Task task) {
    if (task == null || task.memberId <= 0L) {
      return null;
    }
    return people.get(task.memberId);
  }

  private void copyFlyer(Member member) {
    String text = AlumniDesk.flyerInfo(
      member, Calendar.getInstance(), bag, Prefs.alumniAddresses(this));
    if (text == null || text.isEmpty()) {
      Toast.makeText(this, R.string.copy_birthday_empty, Toast.LENGTH_SHORT).show();
      return;
    }
    WhatsAppRouter.copyToClipboard(this, text);
    Toast.makeText(this, R.string.alumni_copied_info, Toast.LENGTH_SHORT).show();
  }

  private void copyAll() {
    List<Member> list = new ArrayList<>();
    for (Task task : cards) {
      Member member = memberOf(task);
      if (member != null) {
        list.add(member);
      }
    }
    String text = AlumniDesk.flyerInfoList(
      list, Calendar.getInstance(), bag, Prefs.alumniAddresses(this));
    if (text == null || text.isEmpty()) {
      Toast.makeText(this, R.string.copy_birthday_empty, Toast.LENGTH_SHORT).show();
      return;
    }
    WhatsAppRouter.copyToClipboard(this, text);
    Toast.makeText(this, R.string.alumni_copied_info, Toast.LENGTH_SHORT).show();
  }

  private void showOptions(Task task) {
    if (task == null) {
      return;
    }
    ArrayList<String> labels = new ArrayList<>();
    labels.add(getString(R.string.alumni_copy_info));
    boolean posted = TaskStatus.POSTED.equals(task.status);
    boolean pending = !posted && TaskStatus.captionWorkPending(task);
    if (pending) {
      labels.add(getString(R.string.caption_ready_action));
    }
    if (!posted) {
      labels.add(getString(task.skipCaption ? R.string.turn_captions_on : R.string.turn_captions_off));
    }
    boolean photo = !posted && task.memberId > 0L;
    if (photo) {
      labels.add(getString(R.string.alumni_got_picture));
      labels.add(getString(R.string.alumni_no_picture));
      Member person = memberOf(task);
      if (person != null && person.personalOnly) {
        labels.add(getString(R.string.alumni_post_group));
      } else {
        labels.add(getString(R.string.alumni_personal_only));
      }
    }
    CharSequence[] items = labels.toArray(new CharSequence[0]);
    new MaterialAlertDialogBuilder(this)
      .setTitle(task.title)
      .setItems(items, (d, which) -> {
        int index = 0;
        if (which == index) {
          copyFlyer(memberOf(task));
          return;
        }
        index++;
        if (pending) {
          if (which == index) {
            Context app = getApplicationContext();
            AppExecutors.disk().execute(() -> ScheduleCoordinator.markCaptionSaved(app, task.id));
            Toast.makeText(this, R.string.caption_ready_toast, Toast.LENGTH_SHORT).show();
            return;
          }
          index++;
        }
        if (!posted) {
          if (which == index) {
            boolean skip = !task.skipCaption;
            Context app = getApplicationContext();
            AppExecutors.disk().execute(() -> ScheduleCoordinator.setSkipCaption(app, task.id, skip));
            Toast.makeText(
              this,
              skip ? R.string.captions_off_toast : R.string.captions_on_toast,
              Toast.LENGTH_SHORT)
              .show();
            return;
          }
          index++;
        }
        if (photo) {
          if (which == index) {
            setPhoto(task, AlumniDesk.PHOTO_GOT);
            return;
          }
          index++;
          if (which == index) {
            setPhoto(task, AlumniDesk.PHOTO_NONE);
            return;
          }
          index++;
          if (which == index) {
            togglePersonal(task);
          }
        }
      })
      .show();
  }

  private void setPhoto(Task task, String status) {
    if (task == null || task.memberId <= 0L) {
      return;
    }
    Context app = getApplicationContext();
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.setAlumniPhoto(app, task.memberId, status);
      AppExecutors.main(() -> {
        Toast.makeText(
          this,
          AlumniDesk.PHOTO_NONE.equals(status)
            ? R.string.alumni_photo_none_saved
            : R.string.alumni_photo_saved,
          Toast.LENGTH_LONG)
          .show();
        load();
      });
    });
  }

  private void togglePersonal(Task task) {
    Member member = memberOf(task);
    if (member == null) {
      return;
    }
    boolean personal = !member.personalOnly;
    Context app = getApplicationContext();
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.setPersonalOnly(app, member.id, personal);
      AppExecutors.main(() -> {
        Toast.makeText(
          this,
          personal ? R.string.alumni_personal_saved : R.string.alumni_group_saved,
          Toast.LENGTH_SHORT)
          .show();
        load();
      });
    });
  }
}
