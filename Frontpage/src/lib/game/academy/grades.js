/** How strong a word is as a move: the icon and colours of each grade (its name and explanation are in the messages, `play.academy.word.grade.*`). */
export const GRADE = {
	FINISH: { icon: 'flag', tone: 'bg-(--kk-danger-soft) text-(color:--kk-danger)' },
	DECISIVE: { icon: 'bolt', tone: 'bg-[#ffe9d6] text-[#d4600a] dark:bg-[#4a2a10] dark:text-[#ffa45c]' },
	VERY_HIGH: { icon: 'local_fire_department', tone: 'bg-(--kk-warn-soft) text-(color:--kk-warn)' },
	HIGH: { icon: 'trending_up', tone: 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' },
	MEDIUM: { icon: 'balance', tone: 'bg-[color-mix(in_srgb,var(--kk-blue)_14%,var(--kk-surface))] text-(color:--kk-blue)' },
	LOW: { icon: 'verified_user', tone: 'bg-(--kk-surface-2) text-(color:--kk-muted)' }
};
export const gradeOf = (word) => (word.attackGrade in GRADE ? word.attackGrade : 'LOW');
