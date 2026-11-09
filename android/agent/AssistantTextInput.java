package os.aios.agent;
import android.content.Context;import android.widget.EditText;import android.text.InputType;import os.aios.core.*;
/** Shared input styling and IME contract for floating and history composers. */
final class AssistantTextInput{
 static EditText create(Context c,String draft){EditText input=DisplayText.editable(new EditText(c));input.setText(draft);input.setTextSize(16);input.setTextColor(AiosTheme.text(c));input.setHintTextColor(AiosTheme.muted(c));input.setHint(c.getString(R.string.assistant_type_your_next_message_8cbbe));input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setPadding(AiosUi.dp(c,8),0,AiosUi.dp(c,8),0);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);return input;}
}
