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
import damjay.publicity.omnipost.util.ExtraKeys;

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
    adapter = new AlumniCaptionsActivity.Adapter(this::open);
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    binding.btnNewAddress.setOnClickListener(v ->
      startActivity(new Intent(requireContext(), AlumniAddressEditActivity.class)));
    return binding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (adapter == null || getContext() == null) {
      return;
    }
    adapter.submit(AlumniCaptionsActivity.catalog(requireContext()));
  }

  private void open(AlumniCaptionsActivity.Row row) {
    if (row == null || row.header) {
      return;
    }
    if (row.address) {
      Intent intent = new Intent(requireContext(), AlumniAddressEditActivity.class);
      intent.putExtra(ExtraKeys.ADDRESS_NAME, row.key);
      startActivity(intent);
      return;
    }
    Intent intent = new Intent(requireContext(), AlumniCaptionEditActivity.class);
    intent.putExtra(ExtraKeys.CAPTION_KEY, row.key);
    startActivity(intent);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
