package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ActivityAlumniDmBinding;
import damjay.publicity.omnipost.scheduler.AlumniCopy;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.share.WhatsAppRouter;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Map;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AlumniDmActivity extends AppCompatActivity {
  private ActivityAlumniDmBinding binding;
  private AlumniDmAdapter adapter;
  private final List<Member> people = new ArrayList<>();
  private String mode = AlumniCopy.KIND_WAVE;
  private long memberId;
  private int month;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityAlumniDmBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnCaptions.setOnClickListener(v ->
      startActivity(new Intent(this, AlumniCaptionsActivity.class)));
    mode = getIntent().getStringExtra(ExtraKeys.ALUMNI_MODE);
    if (mode == null || mode.isEmpty()) {
      mode = AlumniCopy.KIND_WAVE;
    }
    memberId = getIntent().getLongExtra(ExtraKeys.MEMBER_ID, 0L);
    long taskId = getIntent().getLongExtra(ExtraKeys.TASK_ID, 0L);
    month = Calendar.getInstance().get(Calendar.MONTH) + 1;
    adapter = new AlumniDmAdapter((member, caption, openChat) -> send(member, caption, openChat));
    binding.list.setLayoutManager(new LinearLayoutManager(this));
    binding.list.setAdapter(adapter);
    AppExecutors.disk().execute(() -> {
      AppDatabase db = AppDatabase.get(this);
      if (taskId > 0L) {
        Task task = db.taskDao().getById(taskId);
        if (task != null) {
          Calendar post = Calendar.getInstance();
          post.setTimeInMillis(task.postAtMillis);
          month = post.get(Calendar.MONTH) + 1;
          if (TaskTypes.ALUMNI_MONTH.equals(task.type)) {
            mode = AlumniCopy.KIND_BIRTHDAY;
          } else if (TaskTypes.ALUMNI_WAVE.equals(task.type)) {
            mode = AlumniCopy.KIND_WAVE;
          } else if (TaskTypes.ALUMNI_PHOTO.equals(task.type)) {
            mode = AlumniCopy.KIND_PHOTO;
            memberId = task.memberId;
          } else if (TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)) {
            mode = AlumniCopy.KIND_DETAILS;
            memberId = task.memberId;
          }
        }
      }
      List<Member> loaded = pick(db.memberDao().getAllSync(), mode, memberId, month);
      Map<String, String> bag = Prefs.alumniCaptionBag(this);
      String group = AlumniCopy.KIND_BIRTHDAY.equals(mode)
        ? AlumniCopy.group(month, Calendar.getInstance(), bag)
        : "";
      int monthNow = month;
      String title;
      if (AlumniCopy.KIND_PHOTO.equals(mode)) {
        title = getString(R.string.alumni_desk_photo);
      } else if (AlumniCopy.KIND_BIRTHDAY.equals(mode)) {
        title = getString(R.string.alumni_desk_birthday);
      } else {
        title = getString(R.string.alumni_desk_wave);
      }
      AppExecutors.main(() -> {
        binding.title.setText(title);
        binding.subtitle.setText(getString(R.string.alumni_desk_hint));
        boolean showGroup = AlumniCopy.KIND_BIRTHDAY.equals(mode) && memberId <= 0L;
        binding.groupCard.setVisibility(showGroup ? View.VISIBLE : View.GONE);
        if (showGroup) {
          binding.groupPreview.setText(group);
          binding.btnGroup.setOnClickListener(v -> {
            WhatsAppRouter.copyToClipboard(this, group);
            if (!WhatsAppRouter.openApp(this)) {
              Toast.makeText(this, R.string.whatsapp_missing, Toast.LENGTH_LONG).show();
            } else {
              Toast.makeText(this, R.string.alumni_copied_group, Toast.LENGTH_LONG).show();
            }
          });
        }
        people.clear();
        people.addAll(loaded);
        adapter.submit(people, mode, monthNow, bag);
        binding.empty.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
      });
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (adapter == null || people.isEmpty()) {
      return;
    }
    Map<String, String> bag = Prefs.alumniCaptionBag(this);
    adapter.submit(people, mode, month, bag);
    if (AlumniCopy.KIND_BIRTHDAY.equals(mode) && memberId <= 0L && binding != null) {
      binding.groupPreview.setText(AlumniCopy.group(month, Calendar.getInstance(), bag));
    }
  }

  private void send(Member member, String caption, boolean openChat) {
    Context app = getApplicationContext();
    WhatsAppRouter.copyToClipboard(this, caption);
    AppExecutors.disk().execute(() -> ScheduleCoordinator.markAlumniIntroduced(app, member.id));
    boolean ok;
    if (openChat) {
      ok = WhatsAppRouter.openChat(this, member.phone, caption);
    } else {
      ok = true;
    }
    Toast.makeText(
      this,
      openChat
        ? (ok ? R.string.alumni_copied_chat : R.string.whatsapp_missing)
        : R.string.alumni_copied_dm,
      Toast.LENGTH_LONG)
      .show();
  }

  static List<Member> pick(List<Member> all, String mode, long memberId, int month) {
    List<Member> out = new ArrayList<>();
    if (all == null) {
      return out;
    }
    for (Member member : all) {
      if (!Member.isAlumni(member)) {
        continue;
      }
      if (memberId > 0L) {
        if (member.id == memberId) {
          out.add(member);
        }
        continue;
      }
      if (AlumniCopy.KIND_PHOTO.equals(mode)) {
        if (AlumniDesk.wantsPhoto(member) && member.birthMonth == month) {
          out.add(member);
        }
      } else if (AlumniCopy.KIND_BIRTHDAY.equals(mode)) {
        if (member.birthMonth == month) {
          out.add(member);
        }
      } else if (AlumniDesk.inWave(member, month) && member.birthMonth != month) {
        out.add(member);
      }
    }
    return out;
  }
}
