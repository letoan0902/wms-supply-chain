import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import ComparisonSection from './components/ComparisonSection'
import CtaFooter from './components/CtaFooter'
import FaqSection from './components/FaqSection'
import FeaturesSection from './components/FeaturesSection'
import HeroSection from './components/HeroSection'
import Navbar from './components/Navbar'
import PricingSection from './components/PricingSection'
import StatsSection from './components/StatsSection'
import TestimonialsSection from './components/TestimonialsSection'

export default function LandingPage() {
  const location = useLocation()

  const scrollToSection = (sectionId: string) => {
    const element = document.getElementById(sectionId)
    if (element) {
      const navOffset = 80
      const elementPosition = element.getBoundingClientRect().top
      const offsetPosition = elementPosition + window.scrollY - navOffset

      window.scrollTo({
        top: offsetPosition,
        behavior: 'smooth',
      })
    }
  }

  // Nếu người dùng vào route /pricing hoặc có hash #pricing, tự động cuộn đến section tương ứng
  useEffect(() => {
    if (location.pathname === '/pricing' || location.hash === '#pricing') {
      setTimeout(() => scrollToSection('pricing'), 150)
    } else if (location.hash) {
      const id = location.hash.replace('#', '')
      setTimeout(() => scrollToSection(id), 150)
    }
  }, [location.pathname, location.hash])

  return (
    <div className="min-h-screen bg-white font-sans text-slate-800 antialiased selection:bg-blue-600 selection:text-white">
      {/* Sticky Top Navbar */}
      <Navbar onScrollTo={scrollToSection} />

      <main>
        {/* Hero Section */}
        <HeroSection
          onExplorePricing={() => scrollToSection('pricing')}
          onExploreFeatures={() => scrollToSection('features')}
        />

        {/* Platform Numbers & Stats */}
        <StatsSection />

        {/* Key Features Overview */}
        <FeaturesSection />

        {/* Pricing Tiers & Billing Switcher */}
        <PricingSection onScrollToComparison={() => scrollToSection('comparison')} />

        {/* Deep Comparison Matrix */}
        <ComparisonSection />

        {/* Customer Stories */}
        <TestimonialsSection />

        {/* Frequently Asked Questions */}
        <FaqSection />
      </main>

      {/* Call to action & Footer */}
      <CtaFooter onScrollTo={scrollToSection} />
    </div>
  )
}

