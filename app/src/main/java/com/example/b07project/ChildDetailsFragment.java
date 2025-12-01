package com.example.b07project;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.b07project.models.ChildDraft;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Objects;

public class ChildDetailsFragment extends Fragment {

    private ParentSignupViewModel viewModel;
    private int childNumber;
    private int totalChildren;
    private TextInputEditText tietChildFullName;
    private EditText etDob;
    private TextInputEditText tietOptionalNotes;
    private MaterialButton btnNext;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            childNumber = getArguments().getInt("childNumber");
            totalChildren = getArguments().getInt("totalChildren");
        }

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_child_details, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(ParentSignupViewModel.class);

        TextView tvHeader = view.findViewById(R.id.tvChildDetailsHeader);
        tvHeader.setText(getString(R.string.child_details_header, childNumber, totalChildren));

        tietChildFullName = view.findViewById(R.id.tietChildFullName);
        etDob = view.findViewById(R.id.etDob);
        tietOptionalNotes = view.findViewById(R.id.tietOptionalNotes);
        btnNext = view.findViewById(R.id.btnNext);
        btnNext.setEnabled(false);

        tietChildFullName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateInputs();
            }
        });

        etDob.setOnClickListener(v -> showDatePickerDialog());

        view.findViewById(R.id.btnNext).setOnClickListener(v -> {
            ChildDraft child = new ChildDraft();
            child.name = tietChildFullName.getText().toString();
            child.dob = etDob.getText().toString();
            child.notes = tietOptionalNotes.getText().toString();
            viewModel.children.add(child);

            if (childNumber < totalChildren) {
                // Go to the next child
                Bundle args = new Bundle();
                args.putInt("childNumber", childNumber + 1);
                args.putInt("totalChildren", totalChildren);
                ChildDetailsFragment nextFragment = new ChildDetailsFragment();
                nextFragment.setArguments(args);

                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container_view, nextFragment)
                        .addToBackStack(null)
                        .commit();
            } else {
                // Go to the confirmation screen
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container_view, new ConfirmFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        view.findViewById(R.id.btnBack).setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });


        return view;
    }

    private void validateInputs() {
        boolean nameIsReady = !Objects.requireNonNull(tietChildFullName.getText()).toString().trim().isEmpty();
        boolean dobIsReady = !etDob.getText().toString().trim().isEmpty();
        btnNext.setEnabled(nameIsReady && dobIsReady);
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(requireContext(), (view, year1, month1, dayOfMonth) -> {
            String selectedDate = (month1 + 1) + "/" + dayOfMonth + "/" + year1;
            etDob.setText(selectedDate);
            validateInputs();
        }, year, month, day).show();
    }
}
