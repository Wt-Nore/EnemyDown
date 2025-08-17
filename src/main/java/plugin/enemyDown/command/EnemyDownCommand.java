package plugin.enemyDown.command;

//import java.net.http.WebSocket.Listener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Listener;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import plugin.enemyDown.Main;
import plugin.enemyDown.data.PlayerScore;

public class EnemyDownCommand implements CommandExecutor, Listener {

  private Main main;
  private List<PlayerScore> playerScoreList = new ArrayList<>();
  private boolean isGaming = false;


  public EnemyDownCommand(Main main) {
    this.main = main;
  }
  //private Map<Player,Integer>scores;

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, @NotNull String[] args) {
    //体力や空腹ゲージを最大化する
    if (sender instanceof Player player) {
      PlayerScore nowPlayer = getPlayerScore(player);

      nowPlayer.setScore(0);//ゲーム開始時にスコアを初期化する
      System.out.printf("Game Start ! You have Score %d%n ", nowPlayer.getScore());//スコア初期化確認用

      nowPlayer.setGameTime(20);

      isGaming = true;

      World world = player.getWorld();

      initPlayerStatus(player);

      Bukkit.getScheduler().runTaskTimer(main, Runnable -> {
        if(nowPlayer.getGameTime() <= 0) {
          Runnable.cancel();
          player.sendTitle("ゲームが終了しました。",
              nowPlayer.getPlayerName() + " " + nowPlayer.getScore() + "点！",
              0, 60, 0);
          nowPlayer.setScore(0);
          List<Entity> nearbyEntities = player.getNearbyEntities(50, 0, 50);
          for(Entity enemy : nearbyEntities)
            switch (enemy.getType()) {
              case ZOMBIE,SKELETON,SKELETON_HORSE,WITCH -> enemy.remove();
            }

          isGaming = false;

          return;
        }
        world.spawnEntity(getEnemySpawnLocation(player, world), getEnemy());
        nowPlayer.setGameTime(nowPlayer.getGameTime() - 5);
      },0, 5 * 20);
    }
    return false;
  }

  @EventHandler
  public void onEnemyDeath(EntityDeathEvent e) {
    if (!isGaming) {
      return;
    }

    LivingEntity enemy = e.getEntity();
    Player player = enemy.getKiller();
    //Null対策
    if (Objects.isNull(player) || playerScoreList.isEmpty()) {
      return;
    }

    for (PlayerScore playerScore : playerScoreList) {
      if (playerScore.getPlayerName().equals(player.getName())) {
        int point = switch (enemy.getType()) {
          case ZOMBIE -> 10;
          case SKELETON_HORSE -> 15;
          case SKELETON,WITCH -> 20;
          case IRON_GOLEM -> 30;
          default -> 0;
        };
        /**
         * 以下の構文は、ifをswitchに置換した。
        if(EntityType.ZOMBIE.equals(enemy.getType())) {
          point = 10;
        } else if(EntityType.SKELETON_HORSE.equals(enemy.getType())) {
          point = 15;
        } else if(EntityType.SKELETON.equals(enemy.getType())) {
          point = 20;
        } else if(EntityType.IRON_GOLEM.equals(enemy.getType())) {
          point = 30;
        }
        **/

      //EntityType.ZOMBIE,EntityType.SKELETON_HORSE,EntityType.SKELETON,EntityType.IRON_GOLEM);

        playerScore.setScore(playerScore.getScore() + point);
        player.sendMessage("敵を倒した！　現在のスコアは" + playerScore.getScore() + "点！");
      }
    }
  }

    /**
     * 現在実行しているプレイヤーの情報を取得する。
     *  @param player　コマンドを実行したプレイヤー
     *  @return 現在実行しているプレイヤーのスコア情報
     */
    private PlayerScore getPlayerScore(Player player) {
      if(playerScoreList.isEmpty()){
        return addNewPlayer(player);
      } else {
        for(PlayerScore playerScore : playerScoreList) {
          if(!playerScore.getPlayerName().equals(player.getName())) {
            return addNewPlayer(player);
          } else {
            return playerScore;
          }
        }
      }
      return null;
    }

    /**
    if(this.player.getName().equals(player.getName())){
      score += 10;
    }
     **/

  /**
   * 新規のプレイヤー情報をリストに追加します。
   *
   * @param player　コマンドを実行したプレイヤー
   * @return (返されるものは何か)　新規プレイヤー
   */
  private PlayerScore addNewPlayer(Player player) {
    PlayerScore newPlayer = new PlayerScore();
    newPlayer.setPlayerName(player.getName());
    playerScoreList.add(newPlayer);
    return newPlayer;
  }

  /**
   * ゲームを始める前に、プレイヤーの状態を設定する。
   * 体力と空腹度を最大にして、装備をネザーライト一式にする
   * @param player
   */
  private void initPlayerStatus(Player player) {
    player.setHealth(20);
    player.setFoodLevel(20);

    PlayerInventory inventory = player.getInventory();
    inventory.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
    inventory.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
    inventory.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
    inventory.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
    inventory.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
  }

  /**
   * 敵の出現エリアを取得します。
   * 出現エリアはX軸とZ軸は自分の位置からプラス、ランダムで-10〜9の値が設定されます。
   * Y軸はプレイヤーと同じ位置になります。
   *
   * @param player コマンドを実行したプレイヤー
   * @param world コマンドを実行したプレイヤーが所属するワールド
   * @return 敵の出現場所
   */
  @NotNull
  private Location getEnemySpawnLocation(Player player, World world) {
    Location playerLocation = player.getLocation();
    int randomX = new SplittableRandom().nextInt(20) - 10;
    int randomZ = new SplittableRandom().nextInt(20) - 10;

    double x = playerLocation.getX() + randomX;
    double y = playerLocation.getY();
    double z = playerLocation.getZ() + randomZ;

    //System.out.printf("ゾンビが x=%.1f y=%.1f z=%.1f に出現しました%n", x, y, z);
    EntityType enemyType = getEnemy(); // メソッド呼び出しを追加
    System.out.printf("%sが x=%.1f y=%.1f z=%.1f に出現しました%n",
        enemyType.name(), x, y, z);

    return new Location(world, x, y, z);
  }

  /**
   * ランダムで敵を抽選して、その結果の敵を取得します。
   *
   * @return　敵
   */
  private EntityType getEnemy() {
    List<EntityType> enemyList = List.of(EntityType.ZOMBIE,EntityType.SKELETON_HORSE,EntityType.SKELETON,EntityType.WITCH);
    int randomEnemy = new SplittableRandom().nextInt(enemyList.size());
    //リストが二種類なので、0 or 1 が出ればいいので、nextIntの値は2　→　リストの数が変わるたびに一々値を設定するのは面倒なので、値の部分をenemyList.size()にした。
    EntityType enemy = enemyList.get(randomEnemy);

    return enemy;
  }
}
