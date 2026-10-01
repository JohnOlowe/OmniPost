package damjay.publicity.omnipost.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.databinding.FragmentBirthdaysBinding;
import damjay.publicity.omnipost.scheduler.AlumniContacts;
import damjay.publicity.omnipost.scheduler.AlumniCopy;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.BirthdayHorizon;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
  private static final int TAB_MEMBERS = 0;
  private static final int TAB_ALUMNI = 1;
  private static final int TAB_PASTORS = 2;

  private final Map<Integer, List<BirthdayHorizon.Section>> memberByHorizon = new HashMap<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> alumniByHorizon = new HashMap<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> pastorByHorizon = new HashMap<>();
  private int tab = TAB_MEMBERS;
  private int memberHorizon = BirthdayHorizon.ALL;
  private int alumniHorizon = BirthdayHorizon.WEEK;
  private int memberCount;
  private int alumniCount;
  private int pastorCount;
  private boolean ignoreChip;
  private final ActivityResultLauncher<String[]> sheetLauncher =
    registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSheetPicked);

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
      public void onDm(Member member) {
        android.content.Intent intent = new android.content.Intent(requireContext(), AlumniDmActivity.class);
        intent.putExtra(ExtraKeys.MEMBER_ID, member.id);
        intent.putExtra(ExtraKeys.ALUMNI_MODE, AlumniCopy.kindFor(member, Calendar.getInstance().get(Calendar.MONTH) + 1));
        startActivity(intent);
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
    binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_pastors));
    binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      @Override
      public void onTabSelected(TabLayout.Tab selected) {
        tab = selected.getPosition();
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
      if (tab == TAB_MEMBERS) {
        memberHorizon = horizon;
      } else {
        alumniHorizon = horizon;
      }
      paint();
    });
    paintAlumniSwitch();
    binding.btnImportAlumni.setOnClickListener(v ->
      new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.alumni_import)
        .setMessage(R.string.alumni_import_hint)
        .setPositiveButton(R.string.alumni_import, (d, w) -> sheetLauncher.launch(new String[] {
          "text/csv", "text/comma-separated-values", "text/plain", "text/*", "*/*"
        }))
        .setNegativeButton(android.R.string.cancel, null)
        .show());
    binding.btnPairAlumni.setOnClickListener(v ->
      startActivity(new Intent(requireContext(), AlumniMatchActivity.class)));
    binding.btnSaveAlumni.setOnClickListener(v -> saveAlumniContacts());
    binding.btnCopyBirthdays.setOnClickListener(v -> copyBirthdayList());
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
    int horizon = tab == TAB_MEMBERS ? memberHorizon : alumniHorizon;
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
    List<Member> pastors = new ArrayList<>();
    for (Member member : all) {
      if (Member.isPastor(member)) {
        pastors.add(member);
      } else if (Member.isAlumni(member)) {
        alumni.add(member);
      } else {
        members.add(member);
      }
    }
    memberCount = members.size();
    alumniCount = alumni.size();
    pastorCount = pastors.size();
    Calendar now = Calendar.getInstance();
    memberByHorizon.clear();
    alumniByHorizon.clear();
    pastorByHorizon.clear();
    for (int horizon : BirthdayHorizon.HORIZONS) {
      memberByHorizon.put(horizon, BirthdayHorizon.group(members, now, horizon));
      alumniByHorizon.put(horizon, BirthdayHorizon.group(alumni, now, horizon));
      pastorByHorizon.put(horizon, BirthdayHorizon.group(pastors, now, horizon));
    }
  }

  private List<BirthdayHorizon.Section> currentSections() {
    Map<Integer, List<BirthdayHorizon.Section>> byHorizon = memberByHorizon;
    if (tab == TAB_ALUMNI) {
      byHorizon = alumniByHorizon;
    } else if (tab == TAB_PASTORS) {
      byHorizon = pastorByHorizon;
    }
    int horizon = tab == TAB_MEMBERS ? memberHorizon : alumniHorizon;
    List<BirthdayHorizon.Section> sections = byHorizon.get(horizon);
    return sections == null ? Collections.emptyList() : sections;
  }

  private String horizonLabel(int horizon) {
    if (horizon == BirthdayHorizon.TODAY) {
      return getString(R.string.horizon_today);
    }
    if (horizon == BirthdayHorizon.WEEK) {
      return getString(R.string.horizon_week);
    }
    if (horizon == BirthdayHorizon.TWO_WEEKS) {
      return getString(R.string.horizon_fortnight);
    }
    if (horizon == BirthdayHorizon.MONTH) {
      return getString(R.string.horizon_month);
    }
    return getString(R.string.horizon_all);
  }

  private void copyBirthdayList() {
    List<BirthdayHorizon.Section> sections = currentSections();
    String who = tab == TAB_PASTORS
      ? getString(R.string.tab_pastors)
      : tab == TAB_ALUMNI ? getString(R.string.tab_alumni) : getString(R.string.tab_members);
    int horizon = tab == TAB_MEMBERS ? memberHorizon : alumniHorizon;
    String heading = getString(R.string.copy_birthday_heading, who, horizonLabel(horizon));
    String text = BirthdayHorizon.copyList(heading, sections);
    int n = BirthdayHorizon.copyCount(sections);
    if (text.isEmpty() || n == 0) {
      Toast.makeText(requireContext(), R.string.copy_birthday_empty, Toast.LENGTH_SHORT).show();
      return;
    }
    ClipboardManager clipboard =
      (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null) {
      Toast.makeText(requireContext(), R.string.copy_birthday_empty, Toast.LENGTH_SHORT).show();
      return;
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("birthdays", text));
    Toast.makeText(requireContext(), getString(R.string.copy_birthday_copied, n), Toast.LENGTH_SHORT)
      .show();
  }

  private void paint() {
    if (binding == null) {
      return;
    }
    boolean alumniDesk = tab != TAB_MEMBERS;
    List<BirthdayHorizon.Section> sections = currentSections();
    adapter.submit(sections);
    int roster = tab == TAB_PASTORS ? pastorCount : tab == TAB_ALUMNI ? alumniCount : memberCount;
    boolean empty = sections.isEmpty();
    int emptyText = R.string.empty_birthdays;
    if (tab == TAB_ALUMNI) {
      emptyText = R.string.empty_alumni;
    } else if (tab == TAB_PASTORS) {
      emptyText = R.string.empty_pastors;
    }
    binding.empty.setText(empty && roster == 0 ? emptyText : R.string.empty_horizon);
    binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
    binding.rowAlumniCaptions.getRoot().setVisibility(alumniDesk ? View.VISIBLE : View.GONE);
    binding.alumniActions.setVisibility(alumniDesk ? View.VISIBLE : View.GONE);
    binding.fab.setContentDescription(getString(
      tab == TAB_PASTORS
        ? R.string.add_pastor
        : tab == TAB_ALUMNI ? R.string.add_alumni : R.string.add_member));
  }

  private void showEditor(@Nullable Member existing) {
    View view = getLayoutInflater().inflate(R.layout.dialog_member, null, false);
    TextInputEditText name = view.findViewById(R.id.input_name);
    TextInputEditText phone = view.findViewById(R.id.input_phone);
    Spinner month = view.findViewById(R.id.spinner_month);
    Spinner day = view.findViewById(R.id.spinner_day);
    Spinner photo = view.findViewById(R.id.spinner_photo);
    Spinner gender = view.findViewById(R.id.spinner_gender);
    View phoneLayout = view.findViewById(R.id.layout_phone);
    View photoLabel = view.findViewById(R.id.label_photo);
    View genderLabel = view.findViewById(R.id.label_gender);
    CheckBox skipCaption = view.findViewById(R.id.skip_caption);
    CheckBox previousPastor = view.findViewById(R.id.previous_pastor);
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
    ArrayAdapter<CharSequence> photos = ArrayAdapter.createFromResource(
      requireContext(), R.array.alumni_photo_status, R.layout.spinner_item);
    photos.setDropDownViewResource(R.layout.spinner_item);
    photo.setAdapter(photos);
    ArrayAdapter<CharSequence> genders = ArrayAdapter.createFromResource(
      requireContext(), R.array.alumni_gender, R.layout.spinner_item);
    genders.setDropDownViewResource(R.layout.spinner_item);
    gender.setAdapter(genders);
    boolean alumniTab = tab != TAB_MEMBERS;
    if (existing != null) {
      name.setText(existing.name);
      month.setSelection(Math.max(0, existing.birthMonth - 1));
      day.setSelection(Math.max(0, existing.birthDay - 1));
      skipCaption.setChecked(existing.skipCaption);
      previousPastor.setChecked(Member.isPastor(existing));
      if (existing.phone != null) {
        phone.setText(existing.phone);
      }
      photo.setSelection(AlumniDesk.photoSpinnerIndex(existing));
      if (Member.GENDER_MALE.equals(existing.gender)) {
        gender.setSelection(1);
      } else if (Member.GENDER_FEMALE.equals(existing.gender)) {
        gender.setSelection(2);
      } else {
        gender.setSelection(0);
      }
    } else {
      skipCaption.setChecked(alumniTab && Prefs.alumniSkipCaption(requireContext()));
      previousPastor.setChecked(tab == TAB_PASTORS);
    }
    boolean alumni = existing != null ? Member.isAlumni(existing) : alumniTab;
    int alumniVis = alumni ? View.VISIBLE : View.GONE;
    phoneLayout.setVisibility(alumniVis);
    photoLabel.setVisibility(alumniVis);
    photo.setVisibility(alumniVis);
    genderLabel.setVisibility(alumniVis);
    gender.setVisibility(alumniVis);
    previousPastor.setVisibility(alumniVis);
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
        if (alumni) {
          member.desk = previousPastor.isChecked() ? Member.DESK_PASTOR : "";
          member.phone = phone.getText() == null ? "" : phone.getText().toString().trim();
          if (AlumniDesk.nigeriaDigits(member.phone).length() == 13) {
            member.phone = AlumniDesk.nigeriaDigits(member.phone);
          }
          member.photoStatus = AlumniDesk.photoStatusFromIndex(photo.getSelectedItemPosition());
          int g = gender.getSelectedItemPosition();
          member.gender = g == 1 ? Member.GENDER_MALE : g == 2 ? Member.GENDER_FEMALE : "";
        } else {
          member.desk = "";
        }
        AppExecutors.disk().execute(() -> {
          AppDatabase db = AppDatabase.get(requireContext());
          boolean merged = false;
          if (member.id == 0L && alumni) {
            Member prior = db.memberDao().findByKindAndNameIgnoreCase(Member.KIND_ALUMNI, member.name);
            if (prior != null) {
              if (AlumniDesk.nigeriaDigits(member.phone).length() == 13) {
                prior.phone = AlumniDesk.nigeriaDigits(member.phone);
              }
              if (member.gender != null && !member.gender.isEmpty()) {
                prior.gender = member.gender;
              }
              if (member.photoStatus != null && !member.photoStatus.isEmpty()) {
                prior.photoStatus = member.photoStatus;
              }
              prior.skipCaption = member.skipCaption;
              prior.desk = member.desk;
              db.memberDao().update(prior);
              merged = true;
              ScheduleCoordinator.cancelMemberTasks(requireContext(), prior.id);
            }
          }
          if (!merged) {
            if (member.id == 0L) {
              member.id = db.memberDao().insert(member);
            } else {
              db.memberDao().update(member);
              ScheduleCoordinator.cancelMemberTasks(requireContext(), member.id);
            }
          }
          ScheduleCoordinator.bootstrap(requireContext());
          boolean showMerged = merged;
          AppExecutors.main(() -> {
            if (!isAdded() || !showMerged) {
              return;
            }
            Toast.makeText(requireContext(), R.string.alumni_filled_existing, Toast.LENGTH_LONG).show();
          });
        });
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void onSheetPicked(Uri uri) {
    if (uri == null) {
      return;
    }
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      try (InputStream in = app.getContentResolver().openInputStream(uri)) {
        if (in == null) {
          throw new java.io.IOException("sheet");
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        int total = 0;
        while ((n = in.read(chunk)) > 0) {
          buf.write(chunk, 0, n);
          total += n;
          if (total > 2_000_000) {
            throw new java.io.IOException("sheet");
          }
        }
        byte[] bytes = buf.toByteArray();
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
          AppExecutors.main(() -> {
            if (isAdded()) {
              Toast.makeText(requireContext(), R.string.alumni_import_xlsx, Toast.LENGTH_LONG).show();
            }
          });
          return;
        }
        String csv = new String(bytes, StandardCharsets.UTF_8);
        ScheduleCoordinator.AlumniImport stats = ScheduleCoordinator.importAlumniSheet(app, csv);
        AppExecutors.main(() -> {
          if (!isAdded()) {
            return;
          }
          Toast.makeText(
            requireContext(),
            getString(R.string.alumni_import_ok, stats.filled, stats.pending, stats.skipped),
            Toast.LENGTH_LONG)
            .show();
          if (stats.pending > 0) {
            startActivity(new Intent(requireContext(), AlumniMatchActivity.class));
          }
        });
      } catch (Exception e) {
        AppExecutors.main(() -> {
          if (isAdded()) {
            Toast.makeText(requireContext(), R.string.alumni_import_failed, Toast.LENGTH_LONG).show();
          }
        });
      }
    });
  }

  private void saveAlumniContacts() {
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      List<Member> members = AppDatabase.get(app).memberDao().getAllSync();
      int n = AlumniContacts.withPhone(members);
      if (n == 0) {
        AppExecutors.main(() -> {
          if (isAdded()) {
            Toast.makeText(requireContext(), R.string.alumni_save_none, Toast.LENGTH_LONG).show();
          }
        });
        return;
      }
      try {
        java.io.File file = AlumniContacts.writeCache(app, AlumniContacts.csv(members));
        Uri uri = FileProvider.getUriForFile(app, "damjay.publicity.omnipost.files", file);
        AppExecutors.main(() -> {
          if (!isAdded()) {
            return;
          }
          Intent send = new Intent(Intent.ACTION_SEND);
          send.setType("text/csv");
          send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.alumni_save_subject));
          send.putExtra(Intent.EXTRA_TEXT, getString(R.string.alumni_save_body));
          send.putExtra(Intent.EXTRA_STREAM, uri);
          send.setClipData(android.content.ClipData.newRawUri("contacts", uri));
          send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          startActivity(Intent.createChooser(send, getString(R.string.alumni_save_all)));
        });
      } catch (Exception e) {
        AppExecutors.main(() -> {
          if (isAdded()) {
            Toast.makeText(requireContext(), R.string.alumni_save_failed, Toast.LENGTH_LONG).show();
          }
        });
      }
    });
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
