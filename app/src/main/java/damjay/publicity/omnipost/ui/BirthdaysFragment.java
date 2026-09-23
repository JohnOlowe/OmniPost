package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.FragmentBirthdaysBinding;
import damjay.publicity.omnipost.scheduler.BirthdayHorizon;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BirthdaysFragment extends Fragment {
  private FragmentBirthdaysBinding binding;
  private MemberAdapter adapter;
  private final List<Member> all = new ArrayList<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> memberByHorizon = new HashMap<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> alumniByHorizon = new HashMap<>();
  private String kind = Member.KIND_MEMBER;
  private int memberHorizon = BirthdayHorizon.ALL;
  private int alumniHorizon = BirthdayHorizon.WEEK;
  private int memberCount;
  private int alumniCount;
  private boolean ignoreChip;

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentBirthdaysBinding.inflate(inflater, container, false);
    adapter = new MemberAdapter(new MemberAdapter.Listener() {
      @Override
      public void onEdit(Member member) {
        showEditor(member);
      }

      @Override
      public void onDelete(Member member) {
        new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.delete_member_title)
          .setMessage(getString(R.string.delete_member_body, member.name))
          .setPositiveButton(R.string.delete, (d, w) -> {
            Context app = requireContext().getApplicationContext();
            AppExecutors.disk().execute(() -> {
              ScheduleCoordinator.cancelMemberTasks(app, member.id);
              AppDatabase.get(app).memberDao().deleteById(member.id);
            });
          })
          .setNegativeButton(android.R.string.cancel, null)
          .show();
      }
    });
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    binding.list.setHasFixedSize(true);
    binding.list.setItemAnimator(null);
    binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_members));
    binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_alumni));
    binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      @Override
      public void onTabSelected(TabLayout.Tab tab) {
        kind = tab.getPosition() == 1 ? Member.KIND_ALUMNI : Member.KIND_MEMBER;
        syncHorizonChip();
        paint();
      }

      @Override
      public void onTabUnselected(TabLayout.Tab tab) {}

      @Override
      public void onTabReselected(TabLayout.Tab tab) {}
    });
    binding.horizon.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
      if (ignoreChip || !isChecked) {
        return;
      }
      int horizon = horizonOf(checkedId);
      if (Member.KIND_ALUMNI.equals(kind)) {
        alumniHorizon = horizon;
      } else {
        memberHorizon = horizon;
      }
      paint();
    });
    paintAlumniSwitch();
    AppDatabase.get(requireContext())
      .memberDao()
      .observeAll()
      .observe(getViewLifecycleOwner(), members -> {
        all.clear();
        if (members != null) {
          all.addAll(members);
        }
        rebuildCaches();
        paint();
      });
    binding.fab.setOnClickListener(v -> showEditor(null));
    syncHorizonChip();
    return binding.getRoot();
  }

  private void paintAlumniSwitch() {
    if (binding == null) {
      return;
    }
    Context ctx = requireContext();
    binding.rowAlumniCaptions.title.setText(R.string.alumni_captions);
    binding.rowAlumniCaptions.hint.setText(R.string.alumni_captions_hint);
    binding.rowAlumniCaptions.toggle.setOnCheckedChangeListener(null);
    binding.rowAlumniCaptions.toggle.setChecked(!Prefs.alumniSkipCaption(ctx));
    binding.rowAlumniCaptions.toggle.setOnCheckedChangeListener(this::onAlumniCaptionsToggled);
  }

  private void onAlumniCaptionsToggled(CompoundButton button, boolean wantCaptions) {
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.setAlumniSkipCaption(app, !wantCaptions);
      AppExecutors.main(() -> {
        if (!isAdded()) {
          return;
        }
        Toast.makeText(
          requireContext(),
          wantCaptions ? R.string.alumni_captions_on : R.string.alumni_captions_off,
          Toast.LENGTH_SHORT)
          .show();
      });
    });
  }

  private void syncHorizonChip() {
    if (binding == null) {
      return;
    }
    int horizon = Member.KIND_ALUMNI.equals(kind) ? alumniHorizon : memberHorizon;
    int id = R.id.chip_all;
    if (horizon == BirthdayHorizon.TODAY) {
      id = R.id.chip_today;
    } else if (horizon == BirthdayHorizon.WEEK) {
      id = R.id.chip_week;
    } else if (horizon == BirthdayHorizon.TWO_WEEKS) {
      id = R.id.chip_fortnight;
    } else if (horizon == BirthdayHorizon.MONTH) {
      id = R.id.chip_month;
    }
    if (binding.horizon.getCheckedButtonId() == id) {
      return;
    }
    ignoreChip = true;
    binding.horizon.check(id);
    ignoreChip = false;
  }

  private static int horizonOf(int checkedId) {
    if (checkedId == R.id.chip_today) {
      return BirthdayHorizon.TODAY;
    }
    if (checkedId == R.id.chip_week) {
      return BirthdayHorizon.WEEK;
    }
    if (checkedId == R.id.chip_fortnight) {
      return BirthdayHorizon.TWO_WEEKS;
    }
    if (checkedId == R.id.chip_month) {
      return BirthdayHorizon.MONTH;
    }
    return BirthdayHorizon.ALL;
  }

  private void rebuildCaches() {
    List<Member> members = new ArrayList<>();
    List<Member> alumni = new ArrayList<>();
    for (Member member : all) {
      if (Member.KIND_ALUMNI.equals(Member.kindOf(member))) {
        alumni.add(member);
      } else {
        members.add(member);
      }
    }
    memberCount = members.size();
    alumniCount = alumni.size();
    Calendar now = Calendar.getInstance();
    memberByHorizon.clear();
    alumniByHorizon.clear();
    for (int horizon : BirthdayHorizon.HORIZONS) {
      memberByHorizon.put(horizon, BirthdayHorizon.group(members, now, horizon));
      alumniByHorizon.put(horizon, BirthdayHorizon.group(alumni, now, horizon));
    }
  }

  private void paint() {
    if (binding == null) {
      return;
    }
    boolean alumni = Member.KIND_ALUMNI.equals(kind);
    int horizon = alumni ? alumniHorizon : memberHorizon;
    List<BirthdayHorizon.Section> sections =
      (alumni ? alumniByHorizon : memberByHorizon).get(horizon);
    if (sections == null) {
      sections = Collections.emptyList();
    }
    adapter.submit(sections);
    int roster = alumni ? alumniCount : memberCount;
    boolean empty = sections.isEmpty();
    binding.empty.setText(
      empty && roster == 0
        ? (alumni ? R.string.empty_alumni : R.string.empty_birthdays)
        : R.string.empty_horizon);
    binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
    binding.rowAlumniCaptions.getRoot().setVisibility(alumni ? View.VISIBLE : View.GONE);
    binding.fab.setContentDescription(getString(alumni ? R.string.add_alumni : R.string.add_member));
  }

  private void showEditor(@Nullable Member existing) {
    View view = getLayoutInflater().inflate(R.layout.dialog_member, null, false);
    TextInputEditText name = view.findViewById(R.id.input_name);
    Spinner month = view.findViewById(R.id.spinner_month);
    Spinner day = view.findViewById(R.id.spinner_day);
    CheckBox skipCaption = view.findViewById(R.id.skip_caption);
    ArrayAdapter<CharSequence> months = ArrayAdapter.createFromResource(
      requireContext(), R.array.months, R.layout.spinner_item);
    months.setDropDownViewResource(R.layout.spinner_item);
    month.setAdapter(months);
    String[] days = new String[31];
    for (int i = 0; i < 31; i++) {
      days[i] = String.valueOf(i + 1);
    }
    ArrayAdapter<String> dayAdapter =
      new ArrayAdapter<>(requireContext(), R.layout.spinner_item, days);
    dayAdapter.setDropDownViewResource(R.layout.spinner_item);
    day.setAdapter(dayAdapter);
    boolean alumniTab = Member.KIND_ALUMNI.equals(kind);
    if (existing != null) {
      name.setText(existing.name);
      month.setSelection(Math.max(0, existing.birthMonth - 1));
      day.setSelection(Math.max(0, existing.birthDay - 1));
      skipCaption.setChecked(existing.skipCaption);
    } else {
      skipCaption.setChecked(alumniTab && Prefs.alumniSkipCaption(requireContext()));
    }
    boolean alumni = existing != null ? Member.isAlumni(existing) : alumniTab;
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(
        existing == null
          ? (alumni ? R.string.add_alumni : R.string.add_member)
          : (alumni ? R.string.edit_alumni : R.string.edit_member))
      .setView(view)
      .setPositiveButton(alumni ? R.string.save_alumni : R.string.save_member, (d, w) -> {
        String value = name.getText() == null ? "" : name.getText().toString().trim();
        if (value.isEmpty()) {
          return;
        }
        int month1 = month.getSelectedItemPosition() + 1;
        int dayOfMonth = day.getSelectedItemPosition() + 1;
        int max = maxDay(month1);
        if (dayOfMonth > max) {
          dayOfMonth = max;
        }
        Member member = existing == null ? new Member() : existing;
        member.name = value;
        member.birthMonth = month1;
        member.birthDay = dayOfMonth;
        member.kind = alumni ? Member.KIND_ALUMNI : Member.KIND_MEMBER;
        member.skipCaption = skipCaption.isChecked();
        AppExecutors.disk().execute(() -> {
          AppDatabase db = AppDatabase.get(requireContext());
          if (member.id == 0L) {
            member.id = db.memberDao().insert(member);
          } else {
            db.memberDao().update(member);
            ScheduleCoordinator.cancelMemberTasks(requireContext(), member.id);
          }
          ScheduleCoordinator.bootstrap(requireContext());
        });
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private static int maxDay(int month1to12) {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.MONTH, month1to12 - 1);
    c.set(Calendar.DAY_OF_MONTH, 1);
    return c.getActualMaximum(Calendar.DAY_OF_MONTH);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
