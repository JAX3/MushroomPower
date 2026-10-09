# Mycelial Power

**Turn mushrooms and water into Forge Energy, and watch your power grow exponentially as you build bigger generator farms.**

Mycelial Power adds a single machine, the **Mycelial Generator**: an industrial fungal reactor that burns mushrooms, drinks water and pushes **Forge Energy (FE)** into any cable or machine that accepts it. Place generators next to each other and they link into a network that gets stronger with every generator you add. Leave them running and the fungus starts to spread, slowly turning the dirt around them into mycelium.

---

## ⚡ Features

### One machine, done properly
- Burns **red and brown mushrooms**. Any other item or item tag can be added as fuel through the config.
- Built-in **8,000 mB water tank**. Fill it with a bucket, the bucket slot in the GUI, or any fluid pipe.
- **100,000 FE** internal buffer that **auto-outputs** to adjacent cables and machines, so it works with any FE mod.
- Hoppers and pipes can insert mushrooms and filled buckets and pull out the empty buckets.
- Pauses when full, so no mushrooms or water are wasted. It keeps its burn progress through world saves and restarts.
- Comparator output based on stored energy.

### 🍄 Exponential networks
Generators touching on **any of their six faces** automatically form a network. Every running generator boosts all the others:

> **Network FE/t = 40 × N × 1.2^(N − 1)**

| Running generators | Total FE/t |
|---|---|
| 1 | 40 |
| 2 | 96 |
| 3 | 173 |
| 4 | 276 |
| 5 | 415 |
| 10 | 2,064 |

- Only generators that are actually running count, so keep them all fed.
- Networks update instantly as you place or break generators.
- Capped at 64 generators per network by default, with all calculations protected against overflow.
- Never loads chunks: generators in unloaded chunks simply stop counting.

### 🌱 Spreading mycelium
While it runs, a generator tries every few seconds to turn a nearby dirt block into **mycelium**.

- The first attempt has a **1%** chance.
- Every miss adds **+0.5%**, so the longer it goes without spreading, the more likely it gets.
- At 100% the next attempt is **guaranteed**. After a success, the chance resets.
- Respects **claim and protection mods**, spawn protection and the world border.

### 🔥 Fuels

| Fuel | Burn time | Energy | FE/t (single generator) | FE per item |
|---|---|---|---|---|
| Brown Mushroom | 20 s | ×1.0 | 40 | 16,000 |
| Red Mushroom | 30 s | ×1.25 | 50 | 30,000 |

Add mushrooms from other mods with one config line, no code needed:

```
"#c:mushrooms;burn=400;energy=1.0"
"somemod:glowshroom;burn=1200;energy=2.0;mycelium=2.0"
```

Each fuel can set its own **burn time, energy multiplier, water use, mycelium bonus and network scaling bonus**.

### 🖥️ Detailed GUI
- Energy bar, water tank and burn timer.
- Live readouts for stored FE, FE/t generated, FE/t extracted, water use, network size, active generators, multiplier, network total and status.
- Mycelium chance, chance gained per miss, a progress bar to the next attempt, and blocks converted this session.
- Hover over anything for exact numbers and explanations.
- All values come from the server, so it works properly in multiplayer.

### 📖 JEI support (optional)
- A **Mycelial Generator** recipe category listing every valid fuel, including fuels added through the config.
- Burn time, FE/t, total FE per item, water required and multipliers for each fuel.
- A tooltip showing the network scaling table for each fuel.
- An info page explaining the generator and the mycelium mechanic.
- Click the burn indicator in the GUI to see all fuels. The **+** button moves mushrooms from your inventory into the generator.
- Everything JEI shows comes from the server's config, so it always matches the server.

### ⚙️ Fully configurable
Every number lives in `serverconfig/mycelialpower-server.toml`, with comments explaining each setting:

- Base FE/t, scaling multiplier, maximum network size, optional FE/t cap, buffer size and transfer rate.
- Whether idle generators count towards the boost, whether networks merge, and whether they share water and energy.
- Tank size, water use and accepted fluids.
- Every mycelium setting: starting chance, increase per miss, maximum chance, interval, radius, which blocks it converts, light and air requirements, resets, network bonuses.
- Changes apply **while the server is running**, no restart needed.

---

## 🛠️ Crafting

```
 Iron Ingot    Red Mushroom    Iron Ingot
 Brown Mushr.  Cauldron        Brown Mushr.
 Iron Ingot    Redstone        Iron Ingot
```

Mine it with any pickaxe.

---

## 🚀 Getting started
1. Craft a **Mycelial Generator** and place it.
2. Right-click it with a **water bucket**.
3. Open it and put **mushrooms** in the top slot.
4. Run a cable from any side to your machines.
5. Place more generators touching the first one and watch the output climb.

---

## 📋 Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.x |
| Required on | Client **and** server |
| Optional | Just Enough Items (JEI) |

---

## ❓ FAQ

**Does it work with Mekanism, Thermal, Powah and similar mods?**
It outputs standard Forge Energy, so any cable or machine that accepts FE can use it.

**Can I use it in a modpack?**
Yes, go ahead.

**My generator stopped. Why?**
The light on the GUI tells you: red means no fuel, blue means no water, yellow means the energy buffer is full.

**Why isn't my network as strong as I expected?**
By default only **running** generators count. Make sure every generator in the network has both fuel and water.

---

*Mycelial Power is released under the MIT license.*
