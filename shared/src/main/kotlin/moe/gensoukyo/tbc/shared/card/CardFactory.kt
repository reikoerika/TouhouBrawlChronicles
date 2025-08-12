package moe.gensoukyo.tbc.shared.card

import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardType
import java.util.UUID

object CardFactory {
    private val cardTemplates = listOf(
        CardTemplate("杀", CardType.ATTACK, "对目标造成1点伤害", damage = 1),
        CardTemplate("闪", CardType.DEFENSE, "抵消一次攻击", damage = 0),
        CardTemplate("桃", CardType.RECOVERY, "恢复1点生命值", healing = 1),
        CardTemplate("火杀", CardType.ATTACK, "对目标造成2点伤害", damage = 2),
        CardTemplate("雷杀", CardType.ATTACK, "对目标造成1点伤害，并有几率连锁", damage = 1),
        CardTemplate("决斗", CardType.SKILL, "与目标决斗，双方轮流出杀", damage = 1),
        CardTemplate("万箭齐发", CardType.SKILL, "对所有其他玩家造成1点伤害", damage = 1),
        CardTemplate("南蛮入侵", CardType.SKILL, "对所有其他玩家造成1点伤害", damage = 1),
        CardTemplate("无中生有", CardType.SKILL, "摸两张牌", damage = 0),
        CardTemplate("过河拆桥", CardType.SKILL, "弃置目标一张牌", damage = 0)
    )
    
    fun createRandomCard(): Card {
        val template = cardTemplates.random()
        return Card(
            id = UUID.randomUUID().toString(),
            name = template.name,
            type = template.type,
            effect = template.effect,
            damage = template.damage,
            healing = template.healing
        )
    }
    
    fun createCard(name: String): Card? {
        val template = cardTemplates.find { it.name == name } ?: return null
        return Card(
            id = UUID.randomUUID().toString(),
            name = template.name,
            type = template.type,
            effect = template.effect,
            damage = template.damage,
            healing = template.healing
        )
    }
}

private data class CardTemplate(
    val name: String,
    val type: CardType,
    val effect: String,
    val damage: Int = 0,
    val healing: Int = 0
)