package ar.vger32app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.navigation.Navigation;

import ar.vger32app.R;
import ar.vger32app.config.safe.SecurePreferencesManager;
import ar.vger32app.databinding.FragmentUnlockBinding;
import ar.vger32app.logger.LogManager;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.MainActivity;
import ar.vger32app.utils.TrivialCodeValidator;

/*
 * Three modes (ARG_ACTION_MODE):
 * PARAM_ACTION_REQCODE  — verify PIN to unlock the app (launched from AuthActivity)
 * PARAM_ACTION_SETCODE  — set a new PIN when none exists yet (initial setup)
 * PARAM_ACTION_CHGCODE  — change existing PIN: verifies current code first, then sets new one
 * After completing SETCODE or CHGCODE, navigates back to the caller via ARG_DESTINATION_ID.
 * If ARG_DESTINATION_ID is 0 (default), launches MainActivity and finishes
 * the current activity (AuthActivity flow).
 */

public class UnlockFragment extends BaseFragment {

    private static final String LOG_TAG = "UnlockFragment";

    public static final String ARG_ACTION_MODE    = "mode";
    public static final String ARG_DESTINATION_ID = "destinationId";

    public static final int PARAM_ACTION_REQCODE = 1;
    public static final int PARAM_ACTION_SETCODE = 2;
    public static final int PARAM_ACTION_CHGCODE = 3;

    private int actionMode    = PARAM_ACTION_REQCODE;
    private int destinationId = 0;

    private FragmentUnlockBinding binding;

    private String  code            = "";
    private String  reCode          = "";
    private boolean oldCodeVerified = false;

    public UnlockFragment() {
    }

    public static UnlockFragment newInstance(int mode) {
        UnlockFragment instance = new UnlockFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ACTION_MODE, mode);
        instance.setArguments(args);
        return instance;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentUnlockBinding.inflate(inflater, container, false);

        if (getArguments() != null) {
            actionMode    = getArguments().getInt(ARG_ACTION_MODE, PARAM_ACTION_REQCODE);
            destinationId = getArguments().getInt(ARG_DESTINATION_ID, 0);
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initNumKeyboard(true);
        setTitle();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --------------------------------------------------------
    // --- KEYBOARD -------------------------------------------

    private void initNumKeyboard(boolean fingerprintEnabled) {
        binding.btn1.setOnClickListener(v -> onClick("1"));
        binding.btn2.setOnClickListener(v -> onClick("2"));
        binding.btn3.setOnClickListener(v -> onClick("3"));
        binding.btn4.setOnClickListener(v -> onClick("4"));
        binding.btn5.setOnClickListener(v -> onClick("5"));
        binding.btn6.setOnClickListener(v -> onClick("6"));
        binding.btn7.setOnClickListener(v -> onClick("7"));
        binding.btn8.setOnClickListener(v -> onClick("8"));
        binding.btn9.setOnClickListener(v -> onClick("9"));
        binding.btn0.setOnClickListener(v -> onClick("0"));
        binding.btnBS.setOnClickListener(v -> onClick("BS"));

        if (fingerprintEnabled && actionMode == PARAM_ACTION_REQCODE) {
            binding.btnFP.setOnClickListener(v -> onClickFP());
        } else if (fingerprintEnabled && actionMode == PARAM_ACTION_CHGCODE) {
            binding.btnFP.setOnClickListener(v -> onClickFPVerify());
        }
    }

    public void onClick(String id) {
        if ("BS".equals(id)) {
            if (!code.isEmpty()) {
                code = code.substring(0, code.length() - 1);
                paintDots(code.length());
            }
            return;
        }

        code += id;
        paintDots(code.length());

        if (code.length() == 6) doAction();
    }

    // --------------------------------------------------------
    // --- DOTS -----------------------------------------------

    private void paintDots(int len) {
        binding.dot1.setVisibility(len > 0 ? View.VISIBLE : View.GONE);
        binding.dot2.setVisibility(len > 1 ? View.VISIBLE : View.GONE);
        binding.dot3.setVisibility(len > 2 ? View.VISIBLE : View.GONE);
        binding.dot4.setVisibility(len > 3 ? View.VISIBLE : View.GONE);
        binding.dot5.setVisibility(len > 4 ? View.VISIBLE : View.GONE);
        binding.dot6.setVisibility(len > 5 ? View.VISIBLE : View.GONE);
    }

    // --------------------------------------------------------
    // --- BIOMETRIC ------------------------------------------

    public void onClickFP() {
        showBiometricPrompt(() -> navigateAfterUnlock());
    }

    public void onClickFPVerify() {
        showBiometricPrompt(() -> {
            if (binding == null) return;
            oldCodeVerified = true;
            cleanCode();
            binding.btnFP.setEnabled(false);
            binding.lblSubtitle.setText(R.string.set_new_code);
        });
    }

    private void showBiometricPrompt(Runnable onSuccess) {
        BiometricPrompt biometricPrompt = new BiometricPrompt(requireActivity(),
                ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        requireActivity().runOnUiThread(onSuccess);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            LogManager.APP_LOGGER.error(LOG_TAG, getString(R.string.biometric_auth_error));
                            showMessageDialog(getString(R.string.biometric_auth_error), MessageType.ERROR);
                        }
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    // --------------------------------------------------------
    // --- ACTIONS --------------------------------------------

    private void setTitle() {
        switch (actionMode) {
            case PARAM_ACTION_REQCODE: binding.lblSubtitle.setText(R.string.enter_code);         break;
            case PARAM_ACTION_CHGCODE: binding.lblSubtitle.setText(R.string.enter_current_code); break;
            default:                   binding.lblSubtitle.setText(R.string.set_new_code);        break;
        }
    }

    private void doAction() {
        switch (actionMode) {
            case PARAM_ACTION_REQCODE: unlockAction();     break;
            case PARAM_ACTION_SETCODE: setCodeAction();    break;
            case PARAM_ACTION_CHGCODE: changeCodeAction(); break;
        }
    }

    private void unlockAction() {
        if (verifyCode()) {
            navigateAfterUnlock();
        } else {
            cleanCode();
            shake();
        }
    }

    private void setCodeAction() {
        if (reCode.isEmpty()) {
            if (TrivialCodeValidator.isTrivialCode(code)) {
                shake();
                cleanCode();
                return;
            }
            reCode = code;
            cleanCode();
            binding.lblSubtitle.setText(R.string.reenter_new_code);
        } else {
            if (code.equals(reCode)) {
                SecurePreferencesManager.getInstance(requireContext())
                        .saveUserUnlockCode(code)
                        .saveStartDestination(StartDestination.REQ_CODE);
                navigateAfterUnlock();
            } else {
                reCode = "";
                cleanCode();
                shake();
                binding.lblSubtitle.setText(R.string.set_new_code);
            }
        }
    }

    private void changeCodeAction() {
        if (!oldCodeVerified) {
            if (!verifyCode()) { shake(); cleanCode(); return; }
            oldCodeVerified = true;
            cleanCode();
            binding.btnFP.setEnabled(false);
            binding.lblSubtitle.setText(R.string.set_new_code);
            return;
        }
        if (reCode.isEmpty()) {
            if (TrivialCodeValidator.isTrivialCode(code)) {
                shake();
                cleanCode();
                return;
            }
            reCode = code;
            cleanCode();
            binding.lblSubtitle.setText(R.string.reenter_new_code);
        } else {
            if (code.equals(reCode)) {
                SecurePreferencesManager.getInstance(requireContext())
                        .saveUserUnlockCode(code)
                        .saveStartDestination(StartDestination.REQ_CODE);
                navigateAfterUnlock();
            } else {
                reCode = "";
                cleanCode();
                shake();
                binding.lblSubtitle.setText(R.string.set_new_code);
            }
        }
    }

    private void cleanCode() {
        code = "";
        paintDots(0);
    }

    private void shake() {
        Animation anim = AnimationUtils.loadAnimation(requireContext(), R.anim.shake);
        binding.screenLayout.startAnimation(anim);
    }

    private boolean verifyCode() {
        String stored = SecurePreferencesManager.getInstance(requireContext()).getUserUnlockCode();
        return stored != null && stored.equals(code);
    }

    // --------------------------------------------------------
    // --- NAVIGATION -----------------------------------------

    private void navigateAfterUnlock() {
        if (destinationId != 0) {
            Navigation.findNavController(requireView()).popBackStack(destinationId, false);
        } else {
            Intent intent = new Intent(requireActivity(), MainActivity.class);
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            requireActivity().finish();
        }
    }
}