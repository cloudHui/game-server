/** 斗地主：角色角标与操作按钮 */
function roleBadgeHtml(roleId) {
    if (!gameState.landlordId) return '';
    if (roleId === gameState.landlordId) {
        return '<span class="avatar-mark landlord">地</span><span class="role-badge landlord">地主</span>';
    }
    return '<span class="avatar-mark farmer">农</span><span class="role-badge farmer">农民</span>';
}

window.pokerOpChoiceMap = {
    6: { cls: 'btn-play', text: '出牌' },
    0: { cls: 'btn-pass', text: '不出' },
    1: { cls: 'btn-call', text: '叫地主' },
    2: { cls: 'btn-rob', text: '抢地主' },
    3: { cls: 'btn-pass', text: '不叫' },
    4: { cls: 'btn-pass', text: '不抢' },
    9: { cls: 'btn-call', text: '1分' },
    10: { cls: 'btn-call', text: '2分' },
    11: { cls: 'btn-call', text: '3分' }
};
