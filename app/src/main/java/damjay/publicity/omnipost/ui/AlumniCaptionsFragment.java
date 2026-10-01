package damjay.publicity.omnipost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import damjay.publicity.omnipost.databinding.FragmentAlumniCaptionsBinding;
import damjay.publicity.omnipost.scheduler.AlumniTemplates;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.List;

public class AlumniCaptionsFragment extends Fragment {
  private FragmentAlumniCaptionsBinding binding;
  private AlumniCaptionsActivity.Adapter adapter;

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentAlumniCaptionsBinding.inflate(inflater, container, false);
    adapter = new AlumniCaptionsActivity.Adapter(key -> {
      Intent intent = new Intent(requireContext(), AlumniCaptionEditActivity.class);
      intent.putExtra(ExtraKeys.CAPTION_KEY, key);
      startActivity(intent);
    });
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    return binding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (adapter == null || getContext() == null) {
      return;
    }
    List<AlumniCaptionsActivity.Row> rows = new ArrayList<>();
    for (String key : AlumniTemplates.KEYS) {
      AlumniCaptionsActivity.Row row = new AlumniCaptionsActivity.Row();
      row.key = key;
      row.title = AlumniTemplates.label(key);
      String value = Prefs.alumniCaption(requireContext(), key);
      row.preview = value == null ? "" : value.replace('\n', ' ').trim();
      rows.add(row);
    }
    adapter.submit(rows);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
