package sleeper.integrations;

import net.lapismc.afkplus.api.AFKPlusPlayerAPI;
import net.lapismc.afkplus.playerdata.AFKPlusPlayer;

import org.bukkit.entity.Player;

import sleeper.main.Main;

public class AFKPlus {
    
    AFKPlusPlayerAPI api;
    
    public AFKPlus() {
        this.api = new AFKPlusPlayerAPI();
    }
    
    public boolean IsPlayerAFK (Player player) {
        AFKPlusPlayer afkPlusPlayer = api.getPlayer(player);
        return afkPlusPlayer.isAFK();
    }
}