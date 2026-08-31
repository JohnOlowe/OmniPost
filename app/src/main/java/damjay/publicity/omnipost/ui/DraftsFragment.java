package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.databinding.FragmentDraftsBinding;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;

public class DraftsFragment extends Fragment {
  private FragmentDraftsBinding binding;

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentDraftsBinding.inflate(inflater, container, false);
    DraftAdapter adapter = new DraftAdapter(new DraftAdapter.Listener() {
      @Override
      public void onOpen(Draft draft) {
        Intent intent = new Intent(requireContext(), DraftActivity.class);
        intent.putExtra(ExtraKeys.DRAFT_ID, draft.id);
        startActivity(intent);
      }

      @Override
      public void onDelete(Draft draft) {
        new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.delete_draft_title)
          .setMessage(getString(R.string.delete_draft_body, draft.title))
          .setPositiveButton(R.string.delete, (d, w) -> {
            Context app = requireContext().getApplicationContext();
            AppExecutors.disk().execute(() -> ScheduleCoordinator.deleteDraft(app, draft.id));
          })
          .setNegativeButton(android.R.string.cancel, null)
          .show();
      }
    });
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    AppDatabase.get(requireContext())
      .draftDao()
      .observeAll()
      .observe(getViewLifecycleOwner(), drafts -> {
        adapter.submit(drafts);
        binding.empty.setVisibility(drafts == null || drafts.isEmpty() ? View.VISIBLE : View.GONE);
      });
    binding.fab.setOnClickListener(v ->
      startActivity(new Intent(requireContext(), DraftActivity.class)));
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
