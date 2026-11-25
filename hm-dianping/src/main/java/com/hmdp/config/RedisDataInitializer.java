package com.hmdp.config;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * Redis 缓存自愈。
 *
 * <p>黑马点评里有几类 key 属于「写一次、之后一直用」，代码只在业务发生时写入：
 * <ul>
 *     <li>{@code seckill:stock:{voucherId}} —— 只在新建秒杀券时由 VoucherServiceImpl#addSeckillVoucher 写入</li>
 *     <li>{@code shop:geo:{typeId}} —— 只在测试方法 HmDianPingApplicationTests#loadShopData 里导入</li>
 * </ul>
 * 一旦 Redis 被 FLUSHALL / FLUSHDB，这些 key 全部消失，业务会直接报错（库存 key 丢失时
 * Lua 里 tonumber(nil) 会抛错）或查不到数据（GEO 为空则附近商户恒为空）。
 *
 * <p>本类在应用启动后只做「补齐缺失」，已存在的 key 绝不覆盖，避免把线上数据冲掉。
 */
@Slf4j
@Component
public class RedisDataInitializer implements ApplicationRunner {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IShopService shopService;

    @Override
    public void run(ApplicationArguments args) {
        initSeckillStock();
        initShopGeo();
    }

    /**
     * 补齐秒杀库存 key：seckill:stock:{voucherId}。
     * <p>只补缺失的，已存在则跳过（Redis 里的库存才是扣减过程的最新值）。</p>
     */
    private void initSeckillStock() {
        try {
            List<SeckillVoucher> vouchers = seckillVoucherService.list();
            int repaired = 0;
            for (SeckillVoucher voucher : vouchers) {
                String key = SECKILL_STOCK_KEY + voucher.getVoucherId();
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                    continue;
                }
                stringRedisTemplate.opsForValue()
                        .set(key, String.valueOf(voucher.getStock()));
                repaired++;
            }
            if (repaired > 0) {
                log.info("【Redis 自愈】秒杀库存 key 缺失 {} 个，已按数据库库存补齐", repaired);
            }
        } catch (Exception e) {
            log.warn("【Redis 自愈】秒杀库存补齐失败：{}", e.getMessage());
        }
    }

    /**
     * 补齐店铺 GEO 坐标：shop:geo:{typeId}。
     * <p>与 HmDianPingApplicationTests#loadShopData 的写入逻辑一致，按 typeId 分组批量 GEOADD。</p>
     */
    private void initShopGeo() {
        try {
            List<Shop> shops = shopService.list();
            if (shops == null || shops.isEmpty()) {
                return;
            }
            Map<Long, List<Shop>> shopMap = shops.stream()
                    .collect(Collectors.groupingBy(Shop::getTypeId));
            int repaired = 0;
            for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
                String key = SHOP_GEO_KEY + entry.getKey();
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                    continue;
                }
                List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
                for (Shop shop : entry.getValue()) {
                    if (shop.getX() == null || shop.getY() == null) {
                        continue;
                    }
                    locations.add(new RedisGeoCommands.GeoLocation<>(
                            shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())
                    ));
                }
                if (!locations.isEmpty()) {
                    stringRedisTemplate.opsForGeo().add(key, locations);
                    repaired++;
                }
            }
            if (repaired > 0) {
                log.info("【Redis 自愈】店铺 GEO key 缺失 {} 个，已按数据库坐标补齐", repaired);
            }
        } catch (Exception e) {
            log.warn("【Redis 自愈】店铺 GEO 补齐失败：{}", e.getMessage());
        }
    }
}
