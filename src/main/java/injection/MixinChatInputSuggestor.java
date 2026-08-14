package injection;

import cn.remix.util.IMinecraft;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class MixinChatInputSuggestor implements IMinecraft {

    @Shadow @Final
    TextFieldWidget textField;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private ChatInputSuggestor.SuggestionWindow window;

    @Shadow @Final
    private List<OrderedText> messages;

    @Shadow
    boolean completingSuggestions;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void onRefresh(CallbackInfo ci) {
        String text = this.textField.getText().substring(0, this.textField.getCursor());

        if (!text.startsWith(".")) {
            return;
        }

        if (!this.completingSuggestions) {
            this.textField.setSuggestion(null);
            this.window = null;
        }

        this.messages.clear();

        List<String> completions = instance.getCommandManager().getCompletions(text);
        int spaceIndex = text.lastIndexOf(' ');
        int startPos = spaceIndex == -1 ? 1 : spaceIndex + 1;

        SuggestionsBuilder builder = new SuggestionsBuilder(text, startPos);
        completions.forEach(builder::suggest);

        this.pendingSuggestions = builder.buildFuture();

        if (!completions.isEmpty()) {
            this.show(false);
        }

        ci.cancel();
    }
}