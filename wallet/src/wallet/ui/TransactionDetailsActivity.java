package wallet.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Wallet;

import java.text.SimpleDateFormat;
import java.util.Locale;

// --- sửa 3 import dưới đây cho khớp package trong repo của bạn ---
// nếu IDE báo đỏ, đổi wallet.* thành de.schildbach.wallet.*
import wallet.R;
import wallet.WalletApplication;
import wallet.util.WalletUtils;
// ----------------------------------------------------------------

public class TransactionDetailsActivity extends Activity {
    private TextView tvAmount, tvStatus, tvFee, tvTime, tvFrom, tvTo, tvTxid, tvHeight, tvDirection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        tvDirection = findViewById(R.id.tv_direction);
        tvAmount = findViewById(R.id.tv_amount);
        tvStatus = findViewById(R.id.tv_status);
        tvFee = findViewById(R.id.tv_fee);
        tvTime = findViewById(R.id.tv_time);
        tvFrom = findViewById(R.id.tv_from);
        tvTo = findViewById(R.id.tv_to);
        tvTxid = findViewById(R.id.tv_txid);
        tvHeight = findViewById(R.id.tv_height);

        String txidStr = getIntent().getStringExtra("txid");
        if (txidStr == null) { finish(); return; }

        WalletApplication app = (WalletApplication) getApplication();
        Wallet wallet = app.getWallet();
        Transaction tx = wallet.getTransaction(Sha256Hash.wrap(txidStr));
        if (tx == null) { finish(); return; }

        Coin value = tx.getValue(wallet);
        boolean isSend = value.signum() < 0;
        tvDirection.setText(isSend ? "Sent" : "Receive");
        tvAmount.setText((isSend ? "-" : "+") + value.negateIf(isSend).toPlainString() + " BTC");

        TransactionConfidence confidence = tx.getConfidence();
        if (confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
            tvStatus.setText("Success");
            tvStatus.setTextColor(0xFF00C853);
        } else {
            tvStatus.setText("Pending");
            tvStatus.setTextColor(0xFFFFC107);
        }

        Coin fee = tx.getFee();
        tvFee.setText(fee != null ? fee.toPlainString() + " BTC" : "—");

        if (tx.getUpdateTime() != null) {
            tvTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(tx.getUpdateTime()));
        }

        // dùng đúng API của ví, tránh lỗi getFromAddress()
        String fromAddr = "";
        String toAddr = "";
        try {
            if (isSend) {
                toAddr = WalletUtils.getToAddressOfSent(tx, wallet).toString();
                fromAddr = wallet.getWatchedAddresses().isEmpty() ? "My wallet" : wallet.getWatchedAddresses().get(0).toString();
            } else {
                toAddr = WalletUtils.getWalletAddressOfReceived(tx, wallet).toString();
                fromAddr = "External";
            }
        } catch (Exception ignored) {}
        tvFrom.setText(fromAddr);
        tvTo.setText(toAddr);
        copyOnClick(tvFrom, fromAddr);
        copyOnClick(tvTo, toAddr);

        String hash = tx.getTxId().toString();
        tvTxid.setText(hash.substring(0,6) + "…" + hash.substring(hash.length()-6));
        tvTxid.setOnClickListener(v -> copy(hash));

        int height = confidence.getAppearedAtChainHeight();
        tvHeight.setText(height > 0 ? String.valueOf(height) : "—");
    }

    private void copyOnClick(TextView tv, String text) {
        tv.setOnClickListener(v -> copy(text));
    }
    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("tx", text));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }
}
