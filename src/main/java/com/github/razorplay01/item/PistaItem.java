package com.github.razorplay01.item;

import com.github.razorplay01.sound.ModSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PistaItem extends Item {
    /**
     * Nivel con el que se ejecuta el comando guardado en el item, sin importar si el
     * jugador es OP. Alcanza para /mediaplayer, /tp, /playsound y similares, pero deja
     * fuera /op y /stop: si alguien consiguiese un item con el NBT editado, no puede
     * escalar permisos con él.
     */
    private static final int COMMAND_PERMISSION_LEVEL = 2;

    public PistaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            ItemStack stack = player.getItemInHand(hand);

            // El papel suena siempre, haya comando o no: es el gesto de desplegar la hoja.
            level.playSound(null, player.blockPosition(), ModSounds.PAPER_OPEN,
                    SoundSource.PLAYERS, 0.8F, 0.95F + level.random.nextFloat() * 0.1F);

            String command = stack.get(ModComponents.PISTA_COMMAND);

            if (command != null && !command.isEmpty()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    // Añade "/" si no la tiene el usuario
                    String cmd = command.startsWith("/") ? command.substring(1) : command;

                    // El source sigue siendo el jugador (para que @s y ~ ~ ~ apunten a él),
                    // pero se eleva el permiso y se silencia la salida en chat/consola.
                    CommandSourceStack source = serverPlayer.createCommandSourceStack()
                            .withPermission(COMMAND_PERMISSION_LEVEL)
                            .withSuppressedOutput();

                    serverPlayer.server.getCommands().performPrefixedCommand(source, cmd);
                }
            } else {
                player.sendSystemMessage(Component.literal("No hay comando configurado en este item."));
            }
        }

        return super.use(level, player, hand);
    }
}