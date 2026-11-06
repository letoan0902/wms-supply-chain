import { PLATFORM_STATS } from '../pricingConfig'

export default function StatsSection() {
  return (
    <section className="border-y border-slate-200/80 bg-white py-12">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-2 gap-6 md:grid-cols-4 lg:gap-8">
          {PLATFORM_STATS.map((stat, idx) => (
            <div
              key={idx}
              className="flex flex-col items-center text-center p-4 rounded-xl transition-all hover:bg-slate-50"
            >
              <span className="text-3xl font-extrabold tracking-tight text-blue-600 sm:text-4xl lg:text-5xl">
                {stat.value}
              </span>
              <span className="mt-2 text-sm font-semibold text-slate-900 sm:text-base">
                {stat.label}
              </span>
              <span className="mt-1 text-xs text-slate-500">
                {stat.sublabel}
              </span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

