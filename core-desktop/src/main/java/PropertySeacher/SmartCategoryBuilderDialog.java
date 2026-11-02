package PropertySeacher;

import java.awt.Dialog;
import java.util.function.Consumer;

/**
 * @deprecated Use {@link com.propertysearcher.desktop.SmartCategoryBuilderDialog}.
 */
@Deprecated
public class SmartCategoryBuilderDialog extends com.propertysearcher.desktop.SmartCategoryBuilderDialog {
    public SmartCategoryBuilderDialog(Dialog parent, Consumer<String> onCreated) {
        super(parent, onCreated);
    }
}
