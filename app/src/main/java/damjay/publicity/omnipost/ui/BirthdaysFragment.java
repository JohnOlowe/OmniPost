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
import android.text.Editable;
import android.text.TextWatcher;
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
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.AlumniMatch;
import damjay.publicity.omnipost.scheduler.AlumniPending;
import damjay.publicity.omnipost.scheduler.AlumniSheet;
import damjay.publicity.omnipost.scheduler.BirthdayHorizon;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.share.WhatsAppRouter;
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
  public static final String ARG_ALUMNI_HOME = "alumni_home";

  public static BirthdaysFragment alumniPeople() {
    BirthdaysFragment fragment = new BirthdaysFragment();
    Bundle args = new Bundle();
    args.putBoolean(ARG_ALUMNI_HOME, true);
    fragment.setArguments(args);
    return fragment;
  }

  private FragmentBirthdaysBinding binding;
  private MemberAdapter adapter;
  private AlumniMatchActivity.Adapter pairAdapter;
  private final List<Member> all = new ArrayList<>();
  private final List<AlumniSheet.Row> pending = new ArrayList<>();
  private boolean alumniHome;
  private static final int TAB_MEMBERS = 0;
  private static final int TAB_ALUMNI = 1;
  private static final int TAB_PASTORS = 2;
  private static final int TAB_NO_NUMBER = 3;
  private static final int TAB_NO_WHATSAPP = 4;
  private static final int TAB_PAIR = 5;

  private final Map<Integer, List<BirthdayHorizon.Section>> memberByHorizon = new HashMap<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> alumniByHorizon = new HashMap<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> pastorByHorizon = new HashMap<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> noNumberByHorizon = new HashMap<>();
  private final Map<Integer, List<BirthdayHorizon.Section>> noWhatsAppByHorizon = new HashMap<>();
  private int tab = TAB_MEMBERS;
  private int memberHorizon = BirthdayHorizon.ALL;
  private int alumniHorizon = BirthdayHorizon.ALL;
  private int memberCount;
  private int alumniCount;
  private int pastorCount;
  private int noNumberCount;
  private int noWhatsAppCount;
  private boolean ignoreChip;
  private String query = "";
  private final ActivityResultLauncher<String[]> sheetLauncher =
    registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSheetPicked);

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentBirthdaysBinding.inflate(inflater, container, false);
    alumniHome = getArguments() != null && getArguments().getBoolean(ARG_ALUMNI_HOME, false);
    adapter = new MemberAdapter(new MemberAdapter.Listener() {
      @Override
      public void onEdit(Member member) {
        showEditor(member);
      }

      @Override
      public void onOptions(Member member) {
        showPersonOptions(member);
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

      @Override
      public void onToggleSaved(Member member) {
        member.contactSaved = !member.contactSaved;
        AppExecutors.disk().execute(() -> AppDatabase.get(requireContext()).memberDao().update(member));
      }
    });
    adapter.setSavedChip(alumniHome);
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    binding.list.setHasFixedSize(true);
    binding.list.setItemAnimator(null);
    pairAdapter = new AlumniMatchActivity.Adapter((index, row) -> {
      Intent intent = new Intent(requireContext(), AlumniPairActivity.class);
      intent.putExtra(ExtraKeys.PENDING_INDEX, index);
      if (row != null) {
        intent.putExtra(ExtraKeys.PENDING_ROW, row);
      }
      startActivity(intent);
    });
    if (alumniHome) {
      binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_alumni));
      binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_pastors));
      binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_no_number));
      binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_no_whatsapp));
      binding.tabs.addTab(binding.tabs.newTab().setText(R.string.alumni_pair));
      tab = TAB_ALUMNI;
    } else {
      binding.tabs.setVisibility(View.GONE);
      tab = TAB_MEMBERS;
    }
    binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      @Override
      public void onTabSelected(TabLayout.Tab selected) {
        tab = alumniHome ? tabOfAlumni(selected.getPosition()) : TAB_MEMBERS;
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

  @Override
  public void onResume() {
    super.onResume();
    if (alumniHome) {
      reloadPending();
    }
  }

  private static int tabOfAlumni(int position) {
    if (position == 1) {
      return TAB_PASTORS;
    }
    if (position == 2) {
      return TAB_NO_NUMBER;
    }
    if (position == 3) {
      return TAB_NO_WHATSAPP;
    }
    if (position == 4) {
      return TAB_PAIR;
    }
    return TAB_ALUMNI;
  }

  private void reloadPending() {
    if (AlumniPending.cached()) {
      applyPending(AlumniPending.load(requireContext()));
      return;
    }
    AppExecutors.disk().execute(() -> {
      List<AlumniSheet.Row> rows = AlumniPending.load(requireContext());
      AppExecutors.main(() -> applyPending(rows));
    });
  }

  private void applyPending(List<AlumniSheet.Row> rows) {
    if (!isAdded() || pairAdapter == null) {
      return;
    }
    pending.clear();
    if (rows != null) {
      pending.addAll(rows);
    }
    pairAdapter.submit(pending);
    final List<AlumniSheet.Row> waiting = new ArrayList<>(pending);
    AppExecutors.query().execute(() -> AlumniMatch.prepare(waiting));
    paint();
  }

  private void paintPairCount() {
    if (!alumniHome || binding == null || binding.tabs.getTabCount() < 5) {
      return;
    }
    setTabTitle(0, alumniCount, R.string.tab_alumni, R.string.tab_alumni_count);
    setTabTitle(1, pastorCount, R.string.tab_pastors, R.string.tab_pastors_count);
    setTabTitle(2, noNumberCount, R.string.tab_no_number, R.string.tab_no_number_count);
    setTabTitle(3, noWhatsAppCount, R.string.tab_no_whatsapp, R.string.tab_no_whatsapp_count);
    TabLayout.Tab pair = binding.tabs.getTabAt(4);
    if (pair == null) {
      return;
    }
    int n = pending.size();
    pair.setText(n <= 0
      ? getString(R.string.alumni_pair)
      : getString(R.string.alumni_pair_tab_count, n));
  }

  private void setTabTitle(int index, int count, int emptyId, int countedId) {
    TabLayout.Tab tab = binding.tabs.getTabAt(index);
    if (tab == null) {
      return;
    }
    tab.setText(count <= 0 ? getString(emptyId) : getString(countedId, count));
  }

  private void paintAlumniSwitch() {
    if (binding == null) {
      return;
    }
    Context ctx = requireContext();
    binding.captionsToggle.setOnCheckedChangeListener(null);
    binding.captionsToggle.setChecked(!Prefs.alumniSkipCaption(ctx));
    binding.captionsToggle.setOnCheckedChangeListener(this::onAlumniCaptionsToggled);
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
    if (binding == null || tab == TAB_PAIR || tab == TAB_NO_NUMBER || tab == TAB_NO_WHATSAPP) {
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
    List<Member> noNumber = new ArrayList<>();
    List<Member> noWhatsApp = new ArrayList<>();
    for (Member member : all) {
      if (BirthdayHorizon.onPeopleTab(member, true, false, true, false)) {
        noNumber.add(member);
      } else if (BirthdayHorizon.onPeopleTab(member, true, false, false, true)) {
        noWhatsApp.add(member);
      } else if (BirthdayHorizon.onPeopleTab(member, true, true, false, false)) {
        pastors.add(member);
      } else if (BirthdayHorizon.onPeopleTab(member, true, false, false, false)) {
        alumni.add(member);
      } else {
        members.add(member);
      }
    }
    memberCount = members.size();
    alumniCount = alumni.size();
    pastorCount = pastors.size();
    noNumberCount = noNumber.size();
    noWhatsAppCount = noWhatsApp.size();
    Calendar now = Calendar.getInstance();
    memberByHorizon.clear();
    alumniByHorizon.clear();
    pastorByHorizon.clear();
    noNumberByHorizon.clear();
    noWhatsAppByHorizon.clear();
    for (int horizon : BirthdayHorizon.HORIZONS) {
      memberByHorizon.put(horizon, BirthdayHorizon.group(members, now, horizon));
      alumniByHorizon.put(horizon, BirthdayHorizon.group(alumni, now, horizon));
      pastorByHorizon.put(horizon, BirthdayHorizon.group(pastors, now, horizon));
      noNumberByHorizon.put(horizon, BirthdayHorizon.group(noNumber, now, BirthdayHorizon.ALL));
      noWhatsAppByHorizon.put(horizon, BirthdayHorizon.group(noWhatsApp, now, BirthdayHorizon.ALL));
    }
    AlumniMatch.rememberRoster(all);
    final List<AlumniSheet.Row> waiting = new ArrayList<>(pending);
    AppExecutors.query().execute(() -> AlumniMatch.prepare(waiting));
  }

  private List<BirthdayHorizon.Section> currentSections() {
    if (searching()) {
      List<Member> hits = BirthdayHorizon.searchHits(
        all, query, alumniHome, tab == TAB_PASTORS, tab == TAB_NO_NUMBER, tab == TAB_NO_WHATSAPP);
      return BirthdayHorizon.group(hits, Calendar.getInstance(), BirthdayHorizon.ALL);
    }
    Map<Integer, List<BirthdayHorizon.Section>> byHorizon = memberByHorizon;
    if (tab == TAB_ALUMNI) {
      byHorizon = alumniByHorizon;
    } else if (tab == TAB_PASTORS) {
      byHorizon = pastorByHorizon;
    } else if (tab == TAB_NO_NUMBER) {
      byHorizon = noNumberByHorizon;
    } else if (tab == TAB_NO_WHATSAPP) {
      byHorizon = noWhatsAppByHorizon;
    }
    int horizon = tab == TAB_MEMBERS ? memberHorizon : alumniHorizon;
    List<BirthdayHorizon.Section> sections = byHorizon.get(horizon);
    return sections == null ? Collections.emptyList() : sections;
  }

  private boolean searching() {
    return query != null && !query.trim().isEmpty();
  }

  private List<AlumniSheet.Row> pendingMatches() {
    if (!searching()) {
      return pending;
    }
    List<AlumniSheet.Row> hits = new ArrayList<>();
    for (AlumniSheet.Row row : pending) {
      if (BirthdayHorizon.sheetMatches(row, query)) {
        hits.add(row);
      }
    }
    return hits;
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
      : tab == TAB_NO_NUMBER
        ? getString(R.string.tab_no_number)
        : tab == TAB_NO_WHATSAPP
          ? getString(R.string.tab_no_whatsapp)
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
    boolean pairTab = tab == TAB_PAIR;
    boolean noNumberTab = tab == TAB_NO_NUMBER;
    boolean noWhatsAppTab = tab == TAB_NO_WHATSAPP;
    boolean alumniDesk = alumniHome;
    boolean hideHorizon = pairTab || noNumberTab || noWhatsAppTab || searching();
    binding.horizon.setVisibility(hideHorizon ? View.GONE : View.VISIBLE);
    binding.btnCopyBirthdays.setVisibility(pairTab ? View.GONE : View.VISIBLE);
    paintPairCount();
    if (pairTab) {
      if (binding.list.getAdapter() != pairAdapter) {
        binding.list.setAdapter(pairAdapter);
      }
      List<AlumniSheet.Row> rows = pendingMatches();
      pairAdapter.submit(rows);
      boolean empty = rows.isEmpty();
      binding.empty.setText(searching() ? R.string.search_empty : R.string.alumni_pair_empty);
      binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
      binding.captionsToggle.setVisibility(View.GONE);
      binding.alumniActions.setVisibility(View.VISIBLE);
      binding.fab.setVisibility(View.GONE);
      return;
    }
    if (binding.list.getAdapter() != adapter) {
      binding.list.setAdapter(adapter);
    }
    List<BirthdayHorizon.Section> sections = currentSections();
    adapter.submit(sections);
    int roster = tab == TAB_NO_WHATSAPP
      ? noWhatsAppCount
      : tab == TAB_NO_NUMBER
        ? noNumberCount
        : tab == TAB_PASTORS ? pastorCount : tab == TAB_ALUMNI ? alumniCount : memberCount;
    boolean empty = sections.isEmpty();
    int emptyText = R.string.empty_birthdays;
    if (searching()) {
      emptyText = R.string.search_empty;
    } else if (tab == TAB_ALUMNI) {
      emptyText = R.string.empty_alumni;
    } else if (tab == TAB_PASTORS) {
      emptyText = R.string.empty_pastors;
    } else if (tab == TAB_NO_NUMBER) {
      emptyText = R.string.empty_no_number;
    } else if (tab == TAB_NO_WHATSAPP) {
      emptyText = R.string.empty_no_whatsapp;
    }
    binding.empty.setText(searching() || (empty && roster == 0) ? emptyText : R.string.empty_horizon);
    binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
    binding.captionsToggle.setVisibility(alumniDesk ? View.VISIBLE : View.GONE);
    binding.alumniActions.setVisibility(alumniDesk ? View.VISIBLE : View.GONE);
    binding.fab.setVisibility(View.VISIBLE);
    binding.fab.setContentDescription(getString(
      tab == TAB_PASTORS
        ? R.string.add_pastor
        : tab == TAB_NO_NUMBER || tab == TAB_NO_WHATSAPP || tab == TAB_ALUMNI
          ? R.string.add_alumni : R.string.add_member));
  }

  private void showPersonOptions(Member member) {
    if (member == null) {
      return;
    }
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(member.name)
      .setItems(
        new CharSequence[] { getString(R.string.open_whatsapp) },
        (d, which) -> openWhatsApp(member))
      .show();
  }

  private void openWhatsApp(Member member) {
    if (!AlumniDesk.hasPhone(member)) {
      Toast.makeText(requireContext(), R.string.people_open_whatsapp_no_number, Toast.LENGTH_LONG)
        .show();
      return;
    }
    if (!WhatsAppRouter.openNumber(requireContext(), member.phone)) {
      Toast.makeText(requireContext(), R.string.whatsapp_not_installed, Toast.LENGTH_LONG).show();
    }
  }

  private void showEditor(@Nullable Member existing) {
    boolean alumni = existing != null ? Member.isAlumni(existing) : alumniHome;
    if (alumni) {
      MemberEditActivity.open(requireContext(), existing, tab == TAB_PASTORS);
      return;
    }
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
    View contactSaved = view.findViewById(R.id.contact_saved);
    if (contactSaved != null) {
      contactSaved.setVisibility(View.GONE);
    }
    View notOnWhatsAppBox = view.findViewById(R.id.not_on_whatsapp);
    if (notOnWhatsAppBox != null) {
      notOnWhatsAppBox.setVisibility(View.GONE);
    }
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
    boolean alumniTab = alumniHome;
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
    int alumniVis = alumni ? View.VISIBLE : View.GONE;
    phoneLayout.setVisibility(alumniVis);
    photoLabel.setVisibility(alumniVis);
    photo.setVisibility(alumniVis);
    genderLabel.setVisibility(alumniVis);
    gender.setVisibility(alumniVis);
    previousPastor.setVisibility(alumniVis);
    View honor = view.findViewById(R.id.layout_honorific);
    View hnm = view.findViewById(R.id.layout_caption_hnm);
    View details = view.findViewById(R.id.layout_caption_details);
    if (honor != null) {
      honor.setVisibility(alumniVis);
    }
    if (hnm != null) {
      hnm.setVisibility(alumniVis);
    }
    if (details != null) {
      details.setVisibility(alumniVis);
    }
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
          String rawPhone = phone.getText() == null ? "" : phone.getText().toString().trim();
          String storedPhone = AlumniDesk.storePhone(rawPhone);
          member.phone = storedPhone.isEmpty() ? rawPhone : storedPhone;
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
              if (AlumniDesk.hasPhone(member.phone)) {
                prior.phone = AlumniDesk.storePhone(member.phone);
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
        boolean anyPhone = AlumniContacts.withPhone(members, true) > 0;
        AppExecutors.main(() -> {
          if (isAdded()) {
            Toast.makeText(
              requireContext(),
              anyPhone ? R.string.alumni_save_skipped : R.string.alumni_save_none,
              Toast.LENGTH_LONG)
              .show();
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
