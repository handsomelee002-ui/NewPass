package com.gero.newpass.view.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gero.newpass.R;
import com.gero.newpass.view.activities.MainViewActivity;
import com.gero.newpass.view.adapters.CustomAdapter;
import com.gero.newpass.viewmodel.SecurityDashboardViewModel;

public class SecurityDashboardFragment extends Fragment {

    private View bindingView;
    private SecurityDashboardViewModel viewModel;
    private CustomAdapter customAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bindingView = inflater.inflate(R.layout.fragment_security_dashboard, container, false);
        return bindingView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton backBtn = view.findViewById(R.id.backButton);
        TextView weakCountTxt = view.findViewById(R.id.weakCountTxt);
        TextView oldCountTxt = view.findViewById(R.id.oldCountTxt);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        TextView emptyText = view.findViewById(R.id.empty_text);

        backBtn.setOnClickListener(v -> {
            if (getActivity() instanceof MainViewActivity) {
                ((MainViewActivity) getActivity()).onBackPressed();
            }
        });

        viewModel = new ViewModelProvider(this).get(SecurityDashboardViewModel.class);

        customAdapter = new CustomAdapter(getActivity(), getContext(), new java.util.ArrayList<>(), null);
        recyclerView.setAdapter(customAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        viewModel.getFlaggedDataList().observe(getViewLifecycleOwner(), listItems -> {
            if (listItems != null) {
                if (listItems.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    customAdapter.updateData(listItems);
                }
            }
        });

        viewModel.getWeakCount().observe(getViewLifecycleOwner(), count -> {
            weakCountTxt.setText(String.valueOf(count));
        });

        viewModel.getOldCount().observe(getViewLifecycleOwner(), count -> {
            oldCountTxt.setText(String.valueOf(count));
        });

        viewModel.analyzeVault();
    }
}
