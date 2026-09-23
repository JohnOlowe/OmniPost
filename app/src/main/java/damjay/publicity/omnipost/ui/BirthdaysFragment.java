package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
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
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BirthdaysFragment extends Fragment {
  private FragmentBirthdaysBinding binding;
  private MemberAdapter adapter;
  private final List<Member> all = new ArrayList<>();
  private String kind = Member.KIND_MEMBER;

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
    binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_members));
    binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_alumni));
    binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      @Override
      public void onTabSelected(TabLayout.Tab tab) {
        kind = tab.getPosition() == 1 ? Member.KIND_ALUMNI : Member.KIND_MEMBER;
        render();
      }

      @Override
      public void onTabUnselected(TabLayout.Tab tab) {}

      @Override
      public void onTabReselected(TabLayout.Tab tab) {}
    });
    AppDatabase.get(requireContext())
      .memberDao()
      .observeAll()
      .observe(getViewLifecycleOwner(), members -> {
        all.clear();
        if (members != null) {
          all.addAll(members);
        }
        render();
      });
    binding.fab.setOnClickListener(v -> showEditor(null));
    return binding.getRoot();
  }

  private void render() {
    if (binding == null) {
      return;
    }
    List<Member> filtered = new ArrayList<>();
    for (Member member : all) {
      if (kind.equals(Member.kindOf(member))) {
        filtered.add(member);
      }
    }
    adapter.submit(filtered);
    boolean alumni = Member.KIND_ALUMNI.equals(kind);
    binding.empty.setText(alumni ? R.string.empty_alumni : R.string.empty_birthdays);
    binding.empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
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
      skipCaption.setChecked(alumniTab);
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
